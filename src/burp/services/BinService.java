package burp.services;

import burp.api.montoya.MontoyaApi;
import burp.models.RequestBin;
import burp.models.BinServer;
import burp.models.Correlation;

import interactsh.InteractshEntry;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing RequestBin instances
 * Based on BinService from requestbin.saas
 */
public class BinService {
    private final MontoyaApi api;
    private final List<RequestBin> bins;
    private RequestBin activeBin;
    private final RegistrationService registrationService;
    private final PollingService pollingService;
    
    private static final String PREF_BINS = "requestbin.bins";
    private static final String PREF_ACTIVE_BIN = "requestbin.active.bin";

    public BinService(MontoyaApi api, RegistrationService registrationService, PollingService pollingService) {
        this.api = api;
        this.bins = new ArrayList<>();
        this.registrationService = registrationService;
        this.pollingService = pollingService;
        loadPersistedBins();
    }

    /**
     * Create a new bin using server correlation
     * Flow: Get/create correlation -> Generate uniqueId from correlation+id -> Create bin -> Start polling
     */
    public CompletableFuture<RequestBin> createBin(String name, String note, BinServer server) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                api.logging().logToOutput("Creating cloud bin on server: " + server.getName());
                
                // Get or create correlation for this server
                Correlation correlation = registrationService.getOrCreateCorrelation(server).join();
                
                if (correlation == null) {
                    throw new RuntimeException("Failed to create correlation for server: " + server.getName());
                }
                
                // Generate unique ID for bin
                String uniqueId = correlation.getCorrelationId().substring(0, Math.min(20, correlation.getCorrelationId().length()))+ generateUniqueId();
                
                // Generate URL based on server and correlation
                String serverDomain = server.getServerUrl()
                        .replace("https://", "")
                        .replace("http://", "");
                String url = uniqueId + "." + serverDomain;
                
                RequestBin bin = RequestBin.createBin(
                    uniqueId, // Use uniqueId as binId for now
                    name,
                    uniqueId,
                    url,
                    server.getServerId(),
                    server.getServerUrl(),
                    correlation.getCorrelationId()
                );
                bin.setNote(note);
                
                synchronized (bins) {
                    bins.add(bin);
                    setActiveBin(bin);
                    persistBins();
                }
                
                // Start polling for this correlation
                pollingService.startPolling(correlation, uniqueId);
                
                api.logging().logToOutput("Created cloud bin: " + bin.getDisplayName() + " on server: " + server.getName());
                api.logging().logToOutput("Started polling for correlation: " + correlation.getCorrelationId());
                return bin;
                
            } catch (Exception e) {
                api.logging().logToError("Error creating cloud bin: " + e.getMessage());
                throw new RuntimeException("Failed to create cloud bin: " + e.getMessage());
            }
        });
    }

    /**
     * Get all bins
     */
    public List<RequestBin> getAllBins() {
        synchronized (bins) {
            return new ArrayList<>(bins);
        }
    }

    /**
     * Get active bins only
     */
    public List<RequestBin> getActiveBins() {
        synchronized (bins) {
            return bins.stream()
                    .filter(RequestBin::isActive)
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        }
    }

    /**
     * Switch to a specific bin
     */
    public void switchToBin(String binId) {
        synchronized (bins) {
            RequestBin targetBin = bins.stream()
                    .filter(bin -> binId.equals(bin.getBinId()) || binId.equals(bin.getUniqueId()))
                    .findFirst()
                    .orElse(null);
                    
            if (targetBin != null) {
                setActiveBin(targetBin);
                api.logging().logToOutput("Switched to bin: " + targetBin.getDisplayName());
            } else {
                api.logging().logToError("Bin not found: " + binId);
            }
        }
    }

    /**
     * Get currently active bin
     */
    public RequestBin getActiveBin() {
        return activeBin;
    }

    /**
     * Set active bin
     */
    private void setActiveBin(RequestBin bin) {
        this.activeBin = bin;
        if (bin != null) {
            // Persist active bin ID
            api.persistence().preferences().setString(PREF_ACTIVE_BIN, bin.getUniqueId());
        }
    }

    /**
     * Delete a bin
     */
    public boolean deleteBin(String binId) {
        synchronized (bins) {
            RequestBin toRemove = bins.stream()
                    .filter(bin -> binId.equals(bin.getBinId()) || binId.equals(bin.getUniqueId()))
                    .findFirst()
                    .orElse(null);
                    
            if (toRemove != null) {
                api.logging().logToOutput("Deleting bin: " + toRemove.getDisplayName() + " (type: " + toRemove.getType() + ")");
                
                // If it's a cloud bin, stop polling
                if (toRemove.getCorrelationId() != null) {
                    api.logging().logToOutput("Stopping polling for cloud bin correlation: " + toRemove.getCorrelationId());
                    if (pollingService != null) {
                        pollingService.stopPolling(toRemove.getCorrelationId());
                    }
                }
                
                bins.remove(toRemove);
                
                // If deleted bin was active, switch to another bin or clear
                if (activeBin != null && activeBin.equals(toRemove)) {
                    activeBin = bins.isEmpty() ? null : bins.get(0);
                    if (activeBin != null) {
                        api.logging().logToOutput("Switched active bin to: " + activeBin.getDisplayName());
                    } else {
                        api.logging().logToOutput("No bins remaining - cleared active bin");
                    }
                }
                // delete persistence file
                File storageDir = new File(System.getProperty("user.home"), ".requestbin-collaborator");
                File persistenceFile = new File(storageDir, "interactions-" + toRemove.getUniqueId() + ".json");
                if (persistenceFile.exists()) {
                    persistenceFile.delete();
                }
                persistBins();
                api.logging().logToOutput("Successfully deleted bin: " + toRemove.getDisplayName());
                return true;
            }
            
            api.logging().logToError("Bin not found for deletion: " + binId);
            return false;
        }
    }

    /**
     * Update bin details
     */
    public boolean updateBin(String binId, String name, String note) {
        synchronized (bins) {
            RequestBin bin = bins.stream()
                    .filter(b -> binId.equals(b.getBinId()) || binId.equals(b.getUniqueId()))
                    .findFirst()
                    .orElse(null);
                    
            if (bin != null) {
                if (name != null) bin.setName(name);
                if (note != null) bin.setNote(note);
                
                persistBins();
                api.logging().logToOutput("Updated bin: " + bin.getDisplayName());
                return true;
            }
            return false;
        }
    }

    /**
     * Add interaction to active bin
     */
    public void addInteractionToActiveBin(InteractshEntry interaction) {
        if (activeBin != null) {
            activeBin.incrementInteractionCount();
            persistBins(); // Update interaction count
            api.logging().logToOutput("Added interaction to bin: " + activeBin.getDisplayName());
        }
    }
    
    /**
     * Add interaction to specific bin
     */
    public void addInteractionToBin(RequestBin bin, InteractshEntry interaction) {
        if (bin != null) {
            bin.incrementInteractionCount();
            persistBins(); // Update interaction count
            api.logging().logToOutput("Added interaction to bin: " + bin.getDisplayName());
        }
    }

    /**
     * Check if there are any bins
     */
    public boolean hasBins() {
        synchronized (bins) {
            return !bins.isEmpty();
        }
    }

    /**
     * Check if there are any active bins
     */
    public boolean hasActiveBins() {
        synchronized (bins) {
            return bins.stream().anyMatch(RequestBin::isActive);
        }
    }

    /**
     * Generate unique correlation ID (simplified version of the crypto logic)
     */
    // Removed generateCorrelationId - now handled by RegistrationService

    /**
     * Generate unique ID for bin
     */
    private String generateUniqueId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 13);
    }

    /**
     * Load persisted bins from Burp preferences
     */
    private void loadPersistedBins() {
        try {
            String binsJson = api.persistence().preferences().getString(PREF_BINS);
            if (binsJson != null && !binsJson.isEmpty()) {
                JSONArray binsArray = new JSONArray(binsJson);
                // define list of correlation IDs
                List<String> correlationIds = new ArrayList<>();

                for (int i = 0; i < binsArray.length(); i++) {
                    JSONObject binJson = binsArray.getJSONObject(i);
                    RequestBin bin = parseBinFromJSON(binJson);
                    if (bin != null) {
                        bins.add(bin);
                        correlationIds.add(bin.getCorrelationId());
                    }
                }

                // remove correlation in registration service that are not in use
                Map<String, Correlation> allCorrelations = registrationService.getAllCorrelations();
                if (allCorrelations != null) {
                    for (Correlation corr : allCorrelations.values()) {
                        if (!correlationIds.contains(corr.getCorrelationId())) {
                            registrationService.deactivateCorrelationByCorrelationId(corr.getCorrelationId());
                            api.logging().logToOutput("Removed unused correlation: " + corr.getCorrelationId());
                        }
                    }
                }
                
                api.logging().logToOutput("Loaded " + bins.size() + " persisted bins");
                
                // Just update interaction metadata without full loading for now
                // Commenting out full interaction loading at startup to prevent UI freeze
                // updateInteractionMetadata();
                api.logging().logToOutput("[BinService] Skipping interaction loading at startup - will load on-demand");
                
                // Restore active bin
                String activeBinId = api.persistence().preferences().getString(PREF_ACTIVE_BIN);
                if (activeBinId != null) {
                    activeBin = bins.stream()
                            .filter(bin -> activeBinId.equals(bin.getUniqueId()))
                            .findFirst()
                            .orElse(bins.isEmpty() ? null : bins.get(0));
                }
                
                api.logging().logToOutput("[BinService] Bin loading completed - Active bin: " + (activeBin != null ? activeBin.getName() : "none"));
            }
        } catch (Exception e) {
            api.logging().logToError("Error loading persisted bins: " + e.getMessage());
        }
    }

    /**
     * Persist bins to Burp preferences
     */
    private void persistBins() {
        try {
            JSONArray binsArray = new JSONArray();
            
            synchronized (bins) {
                for (RequestBin bin : bins) {
                    JSONObject binJson = binToJSON(bin);
                    binsArray.put(binJson);
                }
            }
            
            api.persistence().preferences().setString(PREF_BINS, binsArray.toString());
        } catch (Exception e) {
            api.logging().logToError("Error persisting bins: " + e.getMessage());
        }
    }

    /**
     * Convert RequestBin to JSON
     */
    private JSONObject binToJSON(RequestBin bin) {
        JSONObject json = new JSONObject();
        json.put("binId", bin.getBinId());
        json.put("name", bin.getName());
        json.put("uniqueId", bin.getUniqueId());
        json.put("url", bin.getUrl());
        json.put("note", bin.getNote());
        json.put("serverId", bin.getServerId());
        json.put("serverUrl", bin.getServerUrl());
        json.put("correlationId", bin.getCorrelationId());
        json.put("isActive", bin.isActive());
        // All bins are now server-based
        json.put("createdAt", bin.getCreatedAt().toString());
        json.put("interactionCount", bin.getInteractionCount());
        
        if (bin.getLastInteractionAt() != null) {
            json.put("lastInteractionAt", bin.getLastInteractionAt().toString());
        }
        
        return json;
    }

    /**
     * Parse RequestBin from JSON
     */
    private RequestBin parseBinFromJSON(JSONObject json) {
        try {
            RequestBin bin = new RequestBin();
            bin.setBinId(json.optString("binId"));
            bin.setName(json.getString("name"));
            bin.setUniqueId(json.getString("uniqueId"));
            bin.setUrl(json.getString("url"));
            bin.setNote(json.optString("note", ""));
            bin.setServerId(json.optString("serverId", ""));
            bin.setServerUrl(json.optString("serverUrl", ""));
            bin.setCorrelationId(json.optString("correlationId", ""));
            bin.setActive(json.optBoolean("isActive", true));
            // All bins are now server-based
            bin.setInteractionCount(json.optInt("interactionCount", 0));
            
            String createdAtStr = json.optString("createdAt");
            if (createdAtStr != null && !createdAtStr.isEmpty()) {
                bin.setCreatedAt(Instant.parse(createdAtStr));
            }
            
            String lastInteractionStr = json.optString("lastInteractionAt");
            if (lastInteractionStr != null && !lastInteractionStr.isEmpty()) {
                bin.setLastInteractionAt(Instant.parse(lastInteractionStr));
            }
            
            return bin;
        } catch (Exception e) {
            api.logging().logToError("Error parsing bin from JSON: " + e.getMessage());
            return null;
        }
    }

    /**
     * Load persisted interactions for all bins
     * Called after bins are loaded to restore interaction history
     * TEMPORARILY DISABLED - causing UI freezing issues
     */
    @SuppressWarnings("unused")
    private void loadPersistedInteractions() {
        try {
            api.logging().logToOutput("[BinService] Starting to load persisted interactions...");
            
            String userHome = System.getProperty("user.home");
            java.io.File storageDir = new java.io.File(userHome, ".requestbin-collaborator");
            
            if (!storageDir.exists()) {
                api.logging().logToOutput("[BinService] No interactions storage directory found - starting fresh");
                return;
            }
            
            int totalInteractions = 0;
            
            synchronized (bins) {
                api.logging().logToOutput("[BinService] Processing " + bins.size() + " bins for interaction loading");
                
                for (RequestBin bin : bins) {
                    if (bin.getCorrelationId() != null && !bin.getCorrelationId().isEmpty()) {
                        try {
                            int binInteractions = loadInteractionsForBin(bin, storageDir);
                            totalInteractions += binInteractions;
                        } catch (Exception e) {
                            api.logging().logToError("[BinService] Error loading interactions for bin " + bin.getName() + ": " + e.getMessage());
                        }
                    }
                }
            }
            
            api.logging().logToOutput("[BinService] Completed loading " + totalInteractions + " persisted interactions across " + bins.size() + " bins");
            
        } catch (Exception e) {
            api.logging().logToError("[BinService] Error loading persisted interactions: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Load interactions for a specific bin from storage
     */
    private int loadInteractionsForBin(RequestBin bin, java.io.File storageDir) {
        try {
            java.io.File dataFile = new java.io.File(storageDir, "interactions-" + bin.getCorrelationId() + ".json");
            
            if (!dataFile.exists()) {
                api.logging().logToOutput("[BinService] No persisted interactions for bin: " + bin.getName());
                return 0;
            }
            
            String existingData = new String(java.nio.file.Files.readAllBytes(dataFile.toPath()));
            JSONArray storedInteractions = new JSONArray(existingData);
            
            api.logging().logToOutput("[BinService] Loading " + storedInteractions.length() + " interactions for bin: " + bin.getName());
            
            // Update bin's interaction count to match stored interactions
            bin.setInteractionCount(storedInteractions.length());
            
            // Update last interaction time if available
            if (storedInteractions.length() > 0) {
                // Find the most recent interaction
                java.time.Instant latestTimestamp = null;
                for (int i = 0; i < storedInteractions.length(); i++) {
                    JSONObject interaction = storedInteractions.getJSONObject(i);
                    String timestampStr = interaction.optString("timestamp", "");
                    
                    if (!timestampStr.isEmpty()) {
                        try {
                            java.time.Instant currentTimestamp = java.time.Instant.parse(timestampStr);
                            if (latestTimestamp == null || currentTimestamp.isAfter(latestTimestamp)) {
                                latestTimestamp = currentTimestamp;
                            }
                        } catch (Exception e) {
                            // Try parsing as long (fallback)
                            try {
                                long timestamp = Long.parseLong(timestampStr);
                                java.time.Instant currentTimestamp = java.time.Instant.ofEpochMilli(timestamp);
                                if (latestTimestamp == null || currentTimestamp.isAfter(latestTimestamp)) {
                                    latestTimestamp = currentTimestamp;
                                }
                            } catch (NumberFormatException e2) {
                                // Skip invalid timestamp
                                api.logging().logToOutput("[BinService] Skipping invalid timestamp: " + timestampStr);
                            }
                        }
                    }
                }
                
                if (latestTimestamp != null) {
                    bin.setLastInteractionAt(latestTimestamp);
                    api.logging().logToOutput("[BinService] Updated last interaction time for bin " + bin.getName() + ": " + latestTimestamp);
                }
            }
            
            api.logging().logToOutput("[BinService] Successfully loaded " + storedInteractions.length() + " interactions for bin: " + bin.getName());
            return storedInteractions.length();
            
        } catch (Exception e) {
            api.logging().logToError("[BinService] Error loading interactions for bin " + bin.getName() + ": " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * Get persisted interactions for a specific bin
     * This can be called by UI components to display interaction history
     */
    public List<InteractshEntry> getPersistedInteractions(RequestBin bin) {
        List<InteractshEntry> interactions = new ArrayList<>();
        
        try {
            if (bin.getCorrelationId() == null || bin.getCorrelationId().isEmpty()) {
                return interactions;
            }
            
            String userHome = System.getProperty("user.home");
            java.io.File storageDir = new java.io.File(userHome, ".requestbin-collaborator");
            java.io.File dataFile = new java.io.File(storageDir, "interactions-" + bin.getCorrelationId() + ".json");
            
            if (!dataFile.exists()) {
                return interactions;
            }
            
            String existingData = new String(java.nio.file.Files.readAllBytes(dataFile.toPath()));
            JSONArray storedInteractions = new JSONArray(existingData);
            
            api.logging().logToOutput("[BinService] Loading " + storedInteractions.length() + " persisted interactions for display in bin: " + bin.getName());
            
            // Convert stored interactions back to InteractshEntry objects
            for (int i = 0; i < storedInteractions.length(); i++) {
                try {
                    JSONObject interaction = storedInteractions.getJSONObject(i);
                    InteractshEntry entry = parseInteractionFromStorage(interaction);
                    if (entry != null) {
                        interactions.add(entry);
                    }
                } catch (Exception e) {
                    api.logging().logToError("[BinService] Error parsing interaction " + i + " for bin " + bin.getName() + ": " + e.getMessage());
                }
            }
            
            api.logging().logToOutput("[BinService] Successfully parsed " + interactions.size() + " interactions for bin: " + bin.getName());
            
        } catch (Exception e) {
            api.logging().logToError("[BinService] Error getting persisted interactions for bin " + bin.getName() + ": " + e.getMessage());
        }
        
        return interactions;
    }
    
    /**
     * Parse InteractshEntry from stored JSON format
     */
    private InteractshEntry parseInteractionFromStorage(JSONObject stored) {
        try {
            // Convert stored format back to the format expected by InteractshEntry constructor
            JSONObject reconstructed = new JSONObject();
            
            // Map fields from storage format to InteractshEntry format
            reconstructed.put("protocol", stored.optString("protocol", "http"));
            reconstructed.put("unique-id", stored.optString("unique-id", ""));
            reconstructed.put("full-id", stored.optString("full-id", ""));
            reconstructed.put("remote-address", stored.optString("remote-address", "unknown"));
            reconstructed.put("raw-request", stored.optString("raw-request", ""));
            reconstructed.put("raw-response", stored.optString("raw-response", ""));
            
            // Handle timestamp - it's already stored as ISO string format
            String timestampStr = stored.optString("timestamp", "");
            if (!timestampStr.isEmpty()) {
                try {
                    // Try to parse as ISO format first (most likely case)
                    java.time.Instant.parse(timestampStr);
                    reconstructed.put("timestamp", timestampStr);
                } catch (Exception e1) {
                    try {
                        // Fallback: try parsing as long (milliseconds)
                        long timestamp = Long.parseLong(timestampStr);
                        java.time.Instant instant = java.time.Instant.ofEpochMilli(timestamp);
                        reconstructed.put("timestamp", instant.toString());
                    } catch (NumberFormatException e2) {
                        api.logging().logToError("[BinService] Invalid timestamp format: " + timestampStr + ", using current time");
                        reconstructed.put("timestamp", java.time.Instant.now().toString());
                    }
                }
            } else {
                reconstructed.put("timestamp", java.time.Instant.now().toString());
            }
            
            // Create InteractshEntry using JSON constructor
            InteractshEntry entry = new InteractshEntry(reconstructed.toString());
            
            // Set read status (this is not part of original format)
            entry.setRead(stored.optBoolean("isViewed", false));
            
            return entry;
            
        } catch (Exception e) {
            api.logging().logToError("[BinService] Error parsing stored interaction: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Clear all bins (for testing or reset purposes)
     */
    public void clearAllBins() {
        synchronized (bins) {
            bins.clear();
            activeBin = null;
            persistBins();
            api.persistence().preferences().setString(PREF_ACTIVE_BIN, null);
        }
        api.logging().logToOutput("Cleared all bins");
    }
}