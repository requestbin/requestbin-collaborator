package burp.services;

import burp.api.montoya.MontoyaApi;
import burp.gui.InteractshTab;
import burp.models.RequestBin;
import burp.models.BinServer;
import burp.models.Correlation;
import interactsh.InteractshEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Central manager for all bin-related operations
 * Coordinates between BinService, RegistrationService, PollingService, and ServerService
 */
public class BinManager {
    private final MontoyaApi api;
    private final BinService binService;
    private final RegistrationService registrationService;
    private final PollingService pollingService;
    private final ServerService serverService;
    private final InteractshTab interactshTab;
    
    public BinManager(MontoyaApi api, InteractshTab interactshTab) {
        this.api = api;
        this.interactshTab = interactshTab;
        
        api.logging().logToOutput("[BinManager] Initializing BinManager and core services...");
        
        // Initialize services in correct order
        api.logging().logToOutput("[BinManager] Creating RegistrationService...");
        this.registrationService = new RegistrationService(api);
        
        api.logging().logToOutput("[BinManager] Creating PollingService with InteractshTab integration...");
        this.pollingService = new PollingService(api, registrationService, this::handleNewInteraction);
        
        api.logging().logToOutput("[BinManager] Creating BinService...");
        this.binService = new BinService(api, registrationService, pollingService);
        
        api.logging().logToOutput("[BinManager] Creating ServerService...");
        this.serverService = new ServerService(api);

        // call to initialize server cache
        this.serverService.getAvailableServers();
        
        api.logging().logToOutput("[BinManager] BinManager initialization completed with all services and UI integration");
    }
    
    /**
     * Get available servers
     */
    public CompletableFuture<List<BinServer>> getAvailableServers() {
        return serverService.getAvailableServers();
    }
    
    /**
     * Create a new bin with server registration and polling
     */
    public CompletableFuture<RequestBin> createBin(String name, String note, BinServer server) {
        api.logging().logToOutput("[BinManager] Creating bin '" + name + "' on server: " + server.getName() + " (" + server.getServerId() + ")");
        return binService.createBin(name, note, server)
                .thenApply(bin -> {
                    api.logging().logToOutput("[BinManager] Successfully created bin: " + bin.getDisplayName() + 
                                            " with URL: " + bin.getUrl());
                    return bin;
                })
                .exceptionally(throwable -> {
                    api.logging().logToError("[BinManager] Failed to create bin '" + name + "': " + throwable.getMessage());
                    throw new RuntimeException(throwable);
                });
    }
    
    /**
     * Get all bins
     */
    public List<RequestBin> getAllBins() {
        return binService.getAllBins();
    }
    
    /**
     * Get active bins only
     */
    public List<RequestBin> getActiveBins() {
        return binService.getActiveBins();
    }
    
    /**
     * Switch to a specific bin
     */
    public void switchToBin(String binId) {
        api.logging().logToOutput("[BinManager] Switching to bin: " + binId);
        
        RequestBin previousBin = binService.getActiveBin();
        binService.switchToBin(binId);
        
        RequestBin activeBin = binService.getActiveBin();
        if (activeBin != null) {
            api.logging().logToOutput("[BinManager] Successfully switched to bin: " + activeBin.getDisplayName() + 
                                    " (type: " + activeBin.getType() + ", previous: " + 
                                    (previousBin != null ? previousBin.getDisplayName() : "none") + ")");
            
            // All bins are now server-based, check if polling needed
            String serverId = activeBin.getServerId();
            Correlation correlation = registrationService.getCorrelation(serverId);
            
            api.logging().logToOutput("[BinManager] Checking polling status for bin - ServerId: " + serverId + 
                                    ", HasCorrelation: " + (correlation != null));
            
            if (correlation != null) {
                boolean isPollingActive = pollingService.isPollingActive(correlation.getCorrelationId());
                api.logging().logToOutput("[BinManager] Polling status for correlation " + correlation.getCorrelationId() + ": " + isPollingActive);
                
                if (!isPollingActive) {
                    api.logging().logToOutput("[BinManager] Starting polling for switched bin: " + activeBin.getDisplayName());
                    pollingService.startPolling(correlation);
                }
            } else {
                api.logging().logToError("[BinManager] No correlation found for bin server: " + serverId);
            }
        } else {
            api.logging().logToError("[BinManager] Failed to switch to bin: " + binId + " (bin not found)");
        }
    }
    
    /**
     * Get currently active bin
     */
    public RequestBin getActiveBin() {
        return binService.getActiveBin();
    }
    
    /**
     * Delete a bin and stop associated polling
     */
    public boolean deleteBin(String binId) {
        api.logging().logToOutput("[BinManager] Deleting bin: " + binId);
        
        RequestBin bin = getAllBins().stream()
                .filter(b -> binId.equals(b.getBinId()) || binId.equals(b.getUniqueId()))
                .findFirst()
                .orElse(null);
        
        if (bin != null) {
            api.logging().logToOutput("[BinManager] Found bin to delete: " + bin.getDisplayName() + 
                                    " (type: " + bin.getType() + ", serverId: " + bin.getServerId() + ")");
            
            // Stop polling for this bin's correlation
            String serverId = bin.getServerId();
            Correlation correlation = registrationService.getCorrelation(serverId);
            
            api.logging().logToOutput("[BinManager] Stopping polling for bin - ServerId: " + serverId + 
                                    ", HasCorrelation: " + (correlation != null));
            
            if (correlation != null) {
                pollingService.stopPolling(correlation.getCorrelationId());
                api.logging().logToOutput("[BinManager] Stopped polling for correlation: " + correlation.getCorrelationId());
            } else {
                api.logging().logToOutput("[BinManager] No correlation found to stop for serverId: " + serverId);
            }
        } else {
            api.logging().logToError("[BinManager] Bin not found for deletion: " + binId);
        }
        
        boolean deleted = binService.deleteBin(binId);
        api.logging().logToOutput("[BinManager] Bin deletion result for " + binId + ": " + deleted);
        return deleted;
    }
    
    /**
     * Update bin details
     */
    public boolean updateBin(String binId, String name, String note) {
        return binService.updateBin(binId, name, note);
    }
    
    /**
     * Clear all bins and stop all polling
     */
    public void clearAllBins() {
        pollingService.stopAllPolling();
        registrationService.clearAllCorrelations();
        binService.clearAllBins();
    }
    
    /**
     * Check if there are any bins
     */
    public boolean hasBins() {
        return binService.hasBins();
    }
    
    /**
     * Check if there are any active bins
     */
    public boolean hasActiveBins() {
        return binService.hasActiveBins();
    }
    
    /**
     * Start polling for all bins
     */
    public void startPollingForAllBins() {
        List<RequestBin> allBins = getAllBins().stream()
                .collect(Collectors.toList());
        
        for (RequestBin bin : allBins) {
            String serverId = bin.getServerId();
            Correlation correlation = registrationService.getCorrelation(serverId);
            if (correlation != null && !pollingService.isPollingActive(correlation.getCorrelationId())) {
                pollingService.startPolling(correlation, bin.getUniqueId());
                api.logging().logToOutput("Started polling for bin: " + bin.getDisplayName());
            }
        }
        
        if (!allBins.isEmpty()) {
            api.logging().logToOutput("Started polling for " + allBins.size() + " bins");
        }
    }
    
    /**
     * Stop polling for all bins
     */
    public void stopAllPolling() {
        pollingService.stopAllPolling();
    }
    
    /**
     * Get polling statistics
     */
    public PollingStats getPollingStats() {
        return new PollingStats(
            pollingService.getActiveTasksCount(),
            registrationService.getAllCorrelations().size()
        );
    }
    
    /**
     * Get PollingService for manual polling operations
     */
    public PollingService getPollingService() {
        return pollingService;
    }
    
    /**
     * Find bin by unique ID (entry.uid matches bin.uniqueId)
     */
    private RequestBin findBinByUniqueId(String uniqueId) {
        if (uniqueId == null || uniqueId.isEmpty()) {
            return null;
        }
        
        return getAllBins().stream()
            .filter(bin -> uniqueId.equals(bin.getUniqueId()))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Handle new interactions from polling
     */
    private void handleNewInteraction(InteractshEntry entry) {
        try {
            // Find the correct bin based on uniqueId from the interaction
            // entry.uid contains the bin's uniqueId (correlationId + generated part)
            // String interactionUniqueId = entry.fullid;
            String interactionUniqueId = entry.uid;
            RequestBin targetBin = findBinByUniqueId(interactionUniqueId);
            
            api.logging().logToOutput("[BinManager] Handling new interaction - UniqueId: " + interactionUniqueId + 
                                    ", TargetBin: " + (targetBin != null ? targetBin.getDisplayName() : "not found"));
            
            if (targetBin != null) {
                // Add interaction to the correct bin
                binService.addInteractionToBin(targetBin, entry);
                api.logging().logToOutput("[BinManager] Added interaction to target bin: " + targetBin.getDisplayName());
                
                // Add to InteractshTab view with bin information
                if (interactshTab != null) {
                    interactshTab.addToTable(entry, targetBin);
                    api.logging().logToOutput("[BinManager] Added interaction to InteractshTab for bin: " + targetBin.getBinId());
                } else {
                    api.logging().logToError("[BinManager] InteractshTab not available - interaction not added to view");
                }
                
                api.logging().logToOutput("[BinManager] Successfully processed interaction for bin: " + targetBin.getDisplayName());
            } else {
                api.logging().logToError("[BinManager] No bin found with uniqueId: " + interactionUniqueId + " - interaction dropped");
                
                // Log available bins for debugging
                api.logging().logToOutput("[BinManager] Available bins:");
                getAllBins().forEach(bin -> {
                    api.logging().logToOutput("  - " + bin.getDisplayName() + " (uniqueId: " + bin.getUniqueId() + 
                                            ", correlationId: " + bin.getCorrelationId() + ")");
                });
            }
            
        } catch (Exception e) {
            api.logging().logToError("[BinManager] Error handling new interaction: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }
    
    /**
     * Perform health check for a server
     */
    public CompletableFuture<BinServer.HealthStatus> performHealthCheck(BinServer server) {
        return serverService.performHealthCheck(server);
    }
    
    /**
     * Clear server cache
     */
    public void clearServerCache() {
        serverService.clearCache();
    }
    
    /**
     * Shutdown all services
     */
    public void shutdown() {
        api.logging().logToOutput("Shutting down BinManager...");
        pollingService.shutdown();
    }
    
    /**
     * Polling statistics container
     */
    public static class PollingStats {
        public final int activePollingTasks;
        public final int totalCorrelations;
        
        public PollingStats(int activePollingTasks, int totalCorrelations) {
            this.activePollingTasks = activePollingTasks;
            this.totalCorrelations = totalCorrelations;
        }
        
        @Override
        public String toString() {
            return String.format("PollingStats{activePollingTasks=%d, totalCorrelations=%d}", 
                    activePollingTasks, totalCorrelations);
        }
    }
}