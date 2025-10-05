package burp.services;

import burp.api.montoya.MontoyaApi;
import burp.models.BinServer;
import burp.models.Correlation;
import burp.utils.CryptoUtils;
import interactsh.InteractshEntry;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import java.net.URI;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Service for polling interactions from cloud servers
 * Based on pollForNewInteractions from requestbin.saas
 */
public class PollingService {
    private final MontoyaApi api;
    private final ScheduledExecutorService scheduler;
    private final Map<String, PollingTask> activeTasks; // correlationId -> PollingTask
    private final Consumer<InteractshEntry> interactionHandler;
    private final RegistrationService registrationService;

    private static final int POLLING_INTERVAL_SECONDS = 30; // Poll every 30 seconds like web version

    public PollingService(MontoyaApi api, RegistrationService registrationService,
                         Consumer<InteractshEntry> interactionHandler) {
        this.api = api;
        this.registrationService = registrationService;
        this.interactionHandler = interactionHandler;
        this.scheduler = Executors.newScheduledThreadPool(5); // Support multiple concurrent polling tasks
        this.activeTasks = new ConcurrentHashMap<>();
    }
    
    /**
     * Start polling for a correlation (deprecated - use version with binUniqueId)
     */
    public void startPolling(Correlation correlation) {
        // For backward compatibility, use correlationId as uniqueId (will be phased out)
        api.logging().logToOutput("[PollingService] Warning: Using deprecated startPolling without binUniqueId");
        startPolling(correlation, correlation.getCorrelationId());
    }
    
    /**
     * Start polling for a correlation
     */
    public void startPolling(Correlation correlation, String binUniqueId) {
        String correlationId = correlation.getCorrelationId();
        // check if correlationid in tasks
        if (activeTasks.containsKey(correlationId)) {
            api.logging().logToOutput("[PollingService] Polling already active for correlation: " + correlationId);
            return;
        }

        if (correlation == null || !correlation.isActive()) {
            api.logging().logToError("[PollingService] Cannot start polling: correlation is " + 
                                   (correlation == null ? "null" : "inactive (" + correlation.getDisplayInfo() + ")"));
            return;
        }
        
        api.logging().logToOutput("[PollingService] Creating polling task for: " + correlationId + 
                                " (bin uniqueId: " + binUniqueId + ", interval: " + POLLING_INTERVAL_SECONDS + "s)");
        
        PollingTask task = new PollingTask(correlation, binUniqueId);
        activeTasks.put(correlationId, task);
        
        // Schedule initial poll after 1 second, then repeat every POLLING_INTERVAL_SECONDS
        task.scheduledFuture = scheduler.scheduleAtFixedRate(
            () -> pollForInteractions(correlation, binUniqueId),
            1, // Initial delay
            POLLING_INTERVAL_SECONDS, // Period
            TimeUnit.SECONDS
        );
    }
    
    /**
     * Stop polling for a correlation
     */
    public void stopPolling(String correlationId) {
        api.logging().logToOutput("[PollingService] Stopping polling for correlation: " + correlationId);
        PollingTask task = activeTasks.remove(correlationId);
        if (task != null && task.scheduledFuture != null) {
            boolean cancelled = task.scheduledFuture.cancel(false);
            api.logging().logToOutput("[PollingService] Stopped polling for correlation: " + correlationId + 
                                    " (cancelled: " + cancelled + ")");
        } else {
            api.logging().logToOutput("[PollingService] No active polling task found for correlation: " + correlationId);
        }
    }
    
    /**
     * Stop all polling tasks
     */
    public void stopAllPolling() {
        api.logging().logToOutput("[PollingService] Stopping all polling tasks (active count: " + activeTasks.size() + ")");
        for (String correlationId : activeTasks.keySet()) {
            stopPolling(correlationId);
        }
        activeTasks.clear();
        api.logging().logToOutput("[PollingService] All polling tasks stopped");
    }
    
    /**
     * Poll for new interactions from server
     * Based on pollForNewInteractions from requestbin.saas
     */
    private void pollForInteractions(Correlation correlation, String binUniqueId) {
        try {
            if (!correlation.isActive()) {
                api.logging().logToOutput("[PollingService] Skipping poll - correlation inactive: " + correlation.getCorrelationId());
                return;
            }
            
            String correlationId = correlation.getCorrelationId();
            String serverUrl = correlation.getServerUrl();
            String secretKey = correlation.getSecretKey();
            String serverToken = correlation.getServerToken();
            
            api.logging().logToOutput("[PollingService] Starting poll cycle - Server: " + serverUrl + 
                                    ", CorrelationId: " + correlationId + 
                                    ", HasToken: " + (serverToken != null && !serverToken.isEmpty()) + 
                                    ", AESKey: " + (correlation.hasAesKey() ? "present" : "missing"));
            
            // Parse server URL to get host, port, scheme
            URI uri = URI.create(serverUrl);
            String host = uri.getHost();
            int port = uri.getPort();
            if (port == -1) {
                port = "https".equals(uri.getScheme()) ? 443 : 80;
            }
            boolean isHttps = "https".equals(uri.getScheme());
            
            // Build raw HTTP request string like InteractshClient
            StringBuilder requestBuilder = new StringBuilder();
            requestBuilder.append("GET /poll?id=").append(correlationId).append("&secret=")
                    .append(secretKey).append(" HTTP/1.1\r\n")
                    .append("Host: ").append(host).append("\r\n")
                    .append("User-Agent: RequestBin-Burp-Extension/1.1\r\n");
            
            // Add authorization header if available
            if (serverToken != null && !serverToken.isEmpty()) {
                requestBuilder.append("Authorization: ").append(serverToken).append("\r\n");
            }
            
            requestBuilder.append("Connection: close\r\n\r\n");
            
            String rawRequest = requestBuilder.toString();
            
            // Create Burp HTTP request
            HttpService httpService = HttpService.httpService(host, port, isHttps);
            HttpRequest httpRequest = HttpRequest.httpRequest(httpService, rawRequest);
            
            api.logging().logToOutput("[PollingService] Sending poll request to: " + serverUrl + "/poll");
            long startTime = System.currentTimeMillis();
            HttpResponse response = api.http().sendRequest(httpRequest).response();
            long responseTime = System.currentTimeMillis() - startTime;
            
            api.logging().logToOutput("[PollingService] Poll response received in " + responseTime + "ms - Status: " + response.statusCode());
            
            if (response.statusCode() != 200) {
                api.logging().logToError("[PollingService] Poll failed with HTTP " + response.statusCode() + " for correlation: " + correlationId);
                handlePollingError(correlation, "HTTP " + response.statusCode(), response.bodyToString());
                return;
            }
            
            String responseBody = response.bodyToString();
            if (responseBody == null || responseBody.isEmpty()) {
                api.logging().logToOutput("[PollingService] No new interactions from polling (empty response)");
                return;
            }
            
            api.logging().logToOutput("[PollingService] Poll response body length: " + responseBody.length());
            
            // Parse poll response
            api.logging().logToOutput("[PollingService] Parsing JSON response for correlation: " + correlationId);
            JSONObject pollData = new JSONObject(responseBody);
            
            // Log response structure
            api.logging().logToOutput("[PollingService] Response structure - hasData: " + pollData.has("data") + 
                                    ", hasError: " + pollData.has("error") + 
                                    ", hasAESKey: " + pollData.has("aes_key"));
            
            // Check for errors in response
            if (pollData.has("error")) {
                String error = pollData.getString("error");
                api.logging().logToError("[PollingService] Server returned error for correlation " + correlationId + ": " + error);
                handlePollingError(correlation, error, responseBody);
                return;
            }
            
            // Process interactions if available
            if (pollData.has("data") && !pollData.isNull("data")) {
                JSONArray data = pollData.getJSONArray("data");
                
                if (data.length() > 0) {
                    api.logging().logToOutput("[PollingService] Received " + data.length() + " interactions from polling for correlation: " + correlationId);
                    processPolledData(correlation, pollData, data, binUniqueId);
                } else {
                    api.logging().logToOutput("[PollingService] No new interactions from polling (empty data array)");
                }
            } else {
                api.logging().logToOutput("[PollingService] No data field in polling response");
            }
            
        } catch (Exception e) {
            api.logging().logToError("[PollingService] Polling failed for correlation " + correlation.getCorrelationId() + 
                                   ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
            if (e.getCause() != null) {
                api.logging().logToError("[PollingService] Caused by: " + e.getCause().getMessage());
            }
        }
    }
    
    /**
     * Process polled interaction data
     * Based on processData from requestbin.saas
     */
    private void processPolledData(Correlation correlation, JSONObject pollData, JSONArray data, String binUniqueId) {
        try {
            String correlationId = correlation.getCorrelationId();
            api.logging().logToOutput("[PollingService] Processing " + data.length() + " interactions for correlation: " + correlationId);
            
            String aesKey = correlation.getAesKey();
            
            // Handle AES key if we got one from server
            if (!correlation.hasAesKey() && pollData.has("aes_key")) {
                String encryptedAESKey = pollData.getString("aes_key");
                api.logging().logToOutput("[PollingService] Received encrypted AES key (length: " + encryptedAESKey.length() + ") for correlation: " + correlationId);
                
                try {
                    // Decrypt AES key using private key
                    api.logging().logToOutput("[PollingService] Decrypting AES key using RSA private key...");
                    PrivateKey privateKey = parsePrivateKey(correlation.getPrivateKey());
                    aesKey = CryptoUtils.decryptAESKey(encryptedAESKey, privateKey);
                    
                    // Update correlation with AES key and persist
                    registrationService.updateAESKey(correlation.getServerId(), aesKey);
                    
                    api.logging().logToOutput("[PollingService] Successfully decrypted and stored AES key (length: " + aesKey.length() + 
                                            ") for correlation: " + correlationId);
                    
                } catch (Exception e) {
                    api.logging().logToError("[PollingService] Failed to decrypt AES key for correlation " + correlationId + ": " + e.getMessage());
                    return;
                }
            } else if (correlation.hasAesKey()) {
                api.logging().logToOutput("[PollingService] Using existing AES key for correlation: " + correlationId);
            }
            
            if (!correlation.hasAesKey()) {
                api.logging().logToError("No AES key available for decryption");
                return;
            }
            
            int successCount = 0;
            int errorCount = 0;
            
            // Process each interaction
            for (int i = 0; i < data.length(); i++) {
                try {
                    String encryptedData = data.getString(i);
                    api.logging().logToOutput("[PollingService] Processing interaction " + (i + 1) + "/" + data.length() + 
                                            " (encrypted length: " + encryptedData.length() + ") for correlation: " + correlationId);
                    
                    // Decrypt interaction data
                    String decryptedData = CryptoUtils.decryptInteractionData(encryptedData, aesKey);
                    api.logging().logToOutput("[PollingService] Successfully decrypted interaction data (length: " + decryptedData.length() + ")");
                    
                    // Create InteractshEntry from decrypted data
                    InteractshEntry entry = new InteractshEntry(decryptedData);
                    api.logging().logToOutput("[PollingService] Created InteractshEntry for interaction " + (i + 1));
                    
                    // Save to local storage (like requestbin.saas)
                    saveInteractionToStorage(entry, correlation, entry.uid);
                    
                    // Handle the interaction (add to UI)
                    if (interactionHandler != null) {
                        interactionHandler.accept(entry);
                        api.logging().logToOutput("[PollingService] Successfully handled interaction " + (i + 1) + " via callback");
                    } else {
                        api.logging().logToError("[PollingService] No interaction handler available for interaction " + (i + 1));
                    }
                    
                    successCount++;
                    
                } catch (Exception e) {
                    errorCount++;
                    api.logging().logToError("[PollingService] Failed to process interaction " + (i + 1) + "/" + data.length() + 
                                           " for correlation " + correlationId + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
                }
            }
            
            api.logging().logToOutput("[PollingService] Interaction processing completed for correlation " + correlationId + 
                                    " - Success: " + successCount + ", Errors: " + errorCount);
            
            // Update correlation increment counter
            correlation.incrementCounter();
            correlation.updateLastUsed();
            
        } catch (Exception e) {
            api.logging().logToError("Error processing polled data: " + e.getMessage());
        }
    }
    
    /**
     * Save interaction to local storage (similar to requestbin.saas format)
     * Saves to ~/.requestbin-collaborator/interactions-{binUniqueId}.json
     */
    private void saveInteractionToStorage(InteractshEntry entry, Correlation correlation, String binUniqueId) {
        try {
            // Create storage directory if not exists
            String userHome = System.getProperty("user.home");
            File storageDir = new File(userHome, ".requestbin-collaborator");
            if (!storageDir.exists()) {
                boolean created = storageDir.mkdirs();
                api.logging().logToOutput("[PollingService] Created storage directory: " + storageDir.getAbsolutePath() + " (success: " + created + ")");
            }
            
            // File for this bin unique ID
            File dataFile = new File(storageDir, "interactions-" + binUniqueId + ".json");
            
            JSONArray storedInteractions;
            if (dataFile.exists()) {
                // Read existing interactions
                try {
                    String existingData = new String(Files.readAllBytes(dataFile.toPath()));
                    storedInteractions = new JSONArray(existingData);
                    api.logging().logToOutput("[PollingService] Loaded " + storedInteractions.length() + " existing interactions from: " + dataFile.getName());
                } catch (Exception e) {
                    api.logging().logToError("[PollingService] Failed to read existing interactions, creating new array: " + e.getMessage());
                    storedInteractions = new JSONArray();
                }
            } else {
                storedInteractions = new JSONArray();
                api.logging().logToOutput("[PollingService] Creating new interactions file: " + dataFile.getName());
            }
            
            // Create storage entry (matching requestbin.saas format)
            JSONObject storageEntry = new JSONObject();
            storageEntry.put("id", String.valueOf(entry.timestamp)); // Use timestamp as ID
            storageEntry.put("full-id", entry.fullid); // url id
            storageEntry.put("protocol", entry.protocol);
            storageEntry.put("raw-request", entry.httpRequest != null ? entry.httpRequest.toString() : entry.details);
            storageEntry.put("raw-response", entry.httpResponse != null ? entry.httpResponse.toString() : "");
            storageEntry.put("remote-address", entry.address);
            storageEntry.put("timestamp", String.valueOf(entry.timestamp));
            storageEntry.put("unique-id", entry.uid); // Link to correlation
            storageEntry.put("isViewed", false); // Default unread
            storageEntry.put("isImportant", false); // Default not important
            
            // Add to array
            storedInteractions.put(storageEntry);
            
            // Write back to file
            Files.write(dataFile.toPath(), 
                       storedInteractions.toString(2).getBytes(), 
                       StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            
            api.logging().logToOutput("[PollingService] Saved interaction to storage: " + dataFile.getAbsolutePath() + 
                                    " (total: " + storedInteractions.length() + " interactions)");
            
        } catch (Exception e) {
            api.logging().logToError("[PollingService] Failed to save interaction to storage: " + e.getMessage());
            if (e instanceof java.nio.file.AccessDeniedException) {
                api.logging().logToError("[PollingService] Access denied - check file permissions for user home directory");
            }
        }
    }
    
    /**
     * Handle polling errors with re-registration logic
     * Based on error handling in requestbin.saas
     */
    private void handlePollingError(Correlation correlation, String error, String responseBody) {
        String correlationId = correlation.getCorrelationId();
        api.logging().logToError("[PollingService] Polling error for correlation " + correlationId + ": " + error);
        api.logging().logToOutput("[PollingService] Error response body: " + (responseBody != null ? responseBody.substring(0, Math.min(500, responseBody.length())) : "null"));
        
        // Handle specific errors that require re-registration
        boolean needsReregistration = error.contains("correlation-id from cache") || error.contains("unauthorized") || error.contains("invalid correlation");
        api.logging().logToOutput("[PollingService] Error analysis - needsReregistration: " + needsReregistration + " for correlation: " + correlationId);
        
        if (needsReregistration) {
            api.logging().logToOutput("[PollingService] Attempting to re-register correlation: " + correlationId + " on server: " + correlation.getServerUrl());
            
            try {
                // Re-register with server
                BinServer server = new BinServer(correlation.getServerId(), "Unknown", 
                                               correlation.getServerUrl(), correlation.getServerToken());
                
                CryptoUtils.RegistrationParams params = new CryptoUtils.RegistrationParams(
                    correlation.getCorrelationId(),
                    correlation.getSecretKey(),
                    correlation.getPublicKey(),
                    correlation.getPrivateKey()
                );
                
                api.logging().logToOutput("[PollingService] Calling registration service for re-registration...");
                boolean success = registrationService.registerWithServer(server, params).join();
                
                if (success) {
                    api.logging().logToOutput("[PollingService] Successfully re-registered correlation: " + correlationId + 
                                            " - Resetting AES key to force re-decryption");
                    // Reset AES key to force re-decryption and persist
                    registrationService.updateAESKey(correlation.getServerId(), "");
                    api.logging().logToOutput("[PollingService] AES key reset completed for correlation: " + correlationId);
                } else {
                    api.logging().logToError("[PollingService] Failed to re-register correlation: " + correlationId + " on server: " + correlation.getServerUrl());
                }
                
            } catch (Exception e) {
                api.logging().logToError("Error during re-registration: " + e.getMessage());
            }
        }
    }
    
    /**
     * Parse private key from PEM string
     */
    private PrivateKey parsePrivateKey(String privateKeyPem) throws Exception {
        String privateKeyContent = privateKeyPem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        
        byte[] keyBytes = Base64.getDecoder().decode(privateKeyContent);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(keySpec);
    }
    
    /**
     * Get active polling tasks count
     */
    public int getActiveTasksCount() {
        return activeTasks.size();
    }
    
    /**
     * Check if polling is active for a correlation
     */
    public boolean isPollingActive(String correlationId) {
        return activeTasks.containsKey(correlationId);
    }
    
    /**
     * Perform a manual poll for a correlation (for refresh functionality)
     */
    public void manualPoll(String correlationId) {
        api.logging().logToOutput("[PollingService] Manual poll requested for correlation: " + correlationId);
        
        // Find the active task for this correlation
        PollingTask task = activeTasks.get(correlationId);
        if (task == null) {
            api.logging().logToError("[PollingService] Cannot manual poll - no active task found for correlation: " + correlationId);
            return;
        }
        
        // Perform immediate poll using existing correlation and binUniqueId
        Correlation correlation = task.getCorrelation();
        String binUniqueId = task.getBinUniqueId();
        
        // Execute poll in a separate thread to avoid blocking UI
        scheduler.execute(() -> {
            api.logging().logToOutput("[PollingService] Executing manual poll for correlation: " + correlationId);
            pollForInteractions(correlation, binUniqueId);
        });
    }
    
    /**
     * Shutdown the polling service
     */
    public void shutdown() {
        api.logging().logToOutput("[PollingService] Shutting down polling service (active tasks: " + activeTasks.size() + ")...");
        stopAllPolling();
        
        api.logging().logToOutput("[PollingService] Shutting down scheduler...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                api.logging().logToError("[PollingService] Scheduler did not terminate in time - forcing shutdown");
                scheduler.shutdownNow();
            } else {
                api.logging().logToOutput("[PollingService] Scheduler terminated gracefully");
            }
        } catch (InterruptedException e) {
            api.logging().logToError("[PollingService] Scheduler shutdown interrupted - forcing shutdown");
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        api.logging().logToOutput("[PollingService] Polling service shutdown completed");
    }
    
    /**
     * Internal class to track polling tasks
     */
    private static class PollingTask {
        final Correlation correlation;
        final String binUniqueId;
        java.util.concurrent.ScheduledFuture<?> scheduledFuture;
        
        PollingTask(Correlation correlation, String binUniqueId) {
            this.correlation = correlation;
            this.binUniqueId = binUniqueId;
        }
        
        public Correlation getCorrelation() {
            return correlation;
        }
        
        public String getBinUniqueId() {
            return binUniqueId;
        }
    }
}