package burp.services;

import burp.api.montoya.MontoyaApi;
import burp.models.BinServer;
import burp.models.Correlation;
import burp.utils.CryptoUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Base64;

import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing server registrations and correlations
 * Based on registerWithServer function from requestbin.saas
 */
public class RegistrationService {
    private final MontoyaApi api;
    private final Map<String, Correlation> correlations; // serverId -> Correlation
    
    private static final String PREF_CORRELATIONS = "requestbin.correlations";
    
    public RegistrationService(MontoyaApi api) {
        this.api = api;
        this.correlations = new HashMap<>();
        api.logging().logToOutput("[RegistrationService] Initializing RegistrationService...");
        loadPersistedCorrelations();
        api.logging().logToOutput("[RegistrationService] RegistrationService initialized with " + correlations.size() + " correlations");
        
        // Log correlation stats if debug mode
        if (correlations.size() > 0) {
            api.logging().logToOutput(getCorrelationStats());
        }
    }
    
    /**
     * Get or create correlation for a server
     * Based on getOrCreateCorrelation from requestbin.saas
     */
    public CompletableFuture<Correlation> getOrCreateCorrelation(BinServer server) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                api.logging().logToOutput("[RegistrationService] Getting/creating correlation for server: " + server.getName() + " (" + server.getServerId() + ")");
                
                // Check if correlation exists for this server
                Correlation correlation = correlations.get(server.getServerId());
                
                if (correlation == null || !correlation.isActive()) {
                    api.logging().logToOutput("[RegistrationService] Creating new correlation for server: " + server.getName() + 
                                            " (existing=" + (correlation != null) + ", active=" + (correlation != null ? correlation.isActive() : "N/A") + ")");
                    
                    // Generate new correlation data
                    api.logging().logToOutput("[RegistrationService] Generating crypto parameters for registration...");
                    CryptoUtils.RegistrationParams params = CryptoUtils.generateRegistrationParams("burp-user");
                    api.logging().logToOutput("[RegistrationService] Generated correlationId: " + params.getCorrelationId() + ", publicKey length: " + params.getPublicKey().length());
                    
                    // Register with the server
                    api.logging().logToOutput("[RegistrationService] Attempting registration with server: " + server.getServerUrl());
                    boolean registrationSuccess = registerWithServer(server, params).join();
                    
                    if (!registrationSuccess) {
                        api.logging().logToError("[RegistrationService] Registration failed for server: " + server.getName());
                        throw new RuntimeException("Failed to register with server: " + server.getName());
                    }
                    api.logging().logToOutput("[RegistrationService] Registration successful for server: " + server.getName());
                    
                    // Create correlation object
                    correlation = new Correlation(
                        params.getCorrelationId(),
                        params.getSecretKey(), 
                        params.getPublicKey(),
                        params.getPrivateKey(),
                        server.getServerId(),
                        server.getServerUrl(),
                        server.getServerToken()
                    );
                    
                    correlations.put(server.getServerId(), correlation);
                    persistCorrelations();
                    
                    api.logging().logToOutput("[RegistrationService] Successfully created and stored correlation: " + correlation.getDisplayInfo());
                    api.logging().logToOutput("[RegistrationService] Total correlations managed: " + correlations.size());
                } else {
                    // Update last used time
                    api.logging().logToOutput("[RegistrationService] Reusing existing correlation: " + correlation.getDisplayInfo());
                    correlation.updateLastUsed();
                    persistCorrelations(); // Save updated last used time
                    api.logging().logToOutput("[RegistrationService] Updated last used time for correlation: " + correlation.getCorrelationId());
                }
                
                return correlation;
                
            } catch (Exception e) {
                api.logging().logToError("Error getting/creating correlation for server " + server.getName() + ": " + e.getMessage());
                throw new RuntimeException("Failed to get/create correlation: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Register with server using correlation data
     * Based on registerWithServer from requestbin.saas libs
     */
    public CompletableFuture<Boolean> registerWithServer(BinServer server, CryptoUtils.RegistrationParams params) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JSONObject registerData = new JSONObject();
                registerData.put("public-key", Base64.getEncoder().encodeToString(params.getPublicKey().getBytes()));
                registerData.put("secret-key", params.getSecretKey());
                registerData.put("correlation-id", params.getCorrelationId());
                
                String requestBody = registerData.toString();
                
                // Parse server URL to get host, port, scheme
                URI uri = URI.create(server.getServerUrl());
                String host = uri.getHost();
                int port = uri.getPort();
                if (port == -1) {
                    port = "https".equals(uri.getScheme()) ? 443 : 80;
                }
                boolean isHttps = "https".equals(uri.getScheme());
                
                // Build raw HTTP request string like InteractshClient
                StringBuilder rawRequestBuilder = new StringBuilder();
                rawRequestBuilder.append("POST /register HTTP/1.1\r\n")
                        .append("Host: ").append(host).append("\r\n")
                        .append("User-Agent: RequestBin-Burp-Extension/1.1\r\n")
                        .append("Content-Type: application/json\r\n")
                        .append("Content-Length: ").append(requestBody.length()).append("\r\n");
                
                // Add authorization header if server has token
                if (server.getServerToken() != null && !server.getServerToken().isEmpty()) {
                    rawRequestBuilder.append("Authorization: ").append(server.getServerToken()).append("\r\n");
                }
                
                rawRequestBuilder.append("Connection: close\r\n\r\n").append(requestBody);
                
                String rawRequest = rawRequestBuilder.toString();
                
                // Create Burp HTTP request
                HttpService httpService = HttpService.httpService(host, port, isHttps);
                HttpRequest httpRequest = HttpRequest.httpRequest(httpService, rawRequest);
                
                api.logging().logToOutput("[RegistrationService] Sending registration request to: " + server.getServerUrl() + "/register");
                api.logging().logToOutput("[RegistrationService] Registration payload - correlationId: " + params.getCorrelationId() + 
                                        ", hasToken: " + (server.getServerToken() != null && !server.getServerToken().isEmpty()) + 
                                        ", publicKeyLength: " + params.getPublicKey().length());
                
                long startTime = System.currentTimeMillis();
                HttpResponse response = api.http().sendRequest(httpRequest).response();
                long responseTime = System.currentTimeMillis() - startTime;
                
                api.logging().logToOutput("[RegistrationService] Registration response received in " + responseTime + "ms - Status: " + response.statusCode());
                
                if (response.statusCode() == 401) {
                    api.logging().logToError("[RegistrationService] Authentication failed for server: " + server.getName() + " - Invalid token");
                    return false;
                }
                
                if (response.statusCode() != 200) {
                    String errorMsg = "Registration failed with status code: " + response.statusCode();
                    String responseBody = response.bodyToString();
                    api.logging().logToOutput("[RegistrationService] Error response body: " + responseBody);
                    
                    try {
                        JSONObject errorData = new JSONObject(responseBody);
                        errorMsg = errorData.optString("error", errorMsg);
                    } catch (Exception ignored) {}
                    
                    api.logging().logToError("[RegistrationService] Registration failed: " + errorMsg);
                    return false;
                }
                
                api.logging().logToOutput("[RegistrationService] Successfully registered correlation " + params.getCorrelationId() + 
                                        " with server " + server.getName() + " (response time: " + responseTime + "ms)");
                return true;
                
            } catch (Exception e) {
                api.logging().logToError("Error registering with server " + server.getName() + ": " + e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * Get correlation for a specific server
     */
    public Correlation getCorrelation(String serverId) {
        return correlations.get(serverId);
    }
    
    /**
     * Get all active correlations
     */
    public Map<String, Correlation> getAllCorrelations() {
        return new HashMap<>(correlations);
    }
    
    /**
     * Deactivate correlation (for cleanup)
     */
    public void deactivateCorrelation(String serverId) {
        api.logging().logToOutput("[RegistrationService] Deactivating correlation for server: " + serverId);
        Correlation correlation = correlations.get(serverId);
        if (correlation != null) {
            api.logging().logToOutput("[RegistrationService] Found correlation to deactivate: " + correlation.getDisplayInfo());
            
            // Send deregistration request to server
            try {
                deregisterFromServer(correlation).join();
            } catch (Exception e) {
                api.logging().logToError("[RegistrationService] Failed to deregister from server: " + e.getMessage());
            }
            
            correlation.setActive(false);
            persistCorrelations();
            api.logging().logToOutput("[RegistrationService] Successfully deactivated correlation for server: " + serverId);
        } else {
            api.logging().logToOutput("[RegistrationService] No correlation found to deactivate for server: " + serverId);
        }
    }
    
    /**
     * Deactivate correlation by correlation ID
     */
    public void deactivateCorrelationByCorrelationId(String correlationId) {
        api.logging().logToOutput("[RegistrationService] Deactivating correlation by ID: " + correlationId);
        
        for (Correlation correlation : correlations.values()) {
            if (correlation != null && correlationId.equals(correlation.getCorrelationId())) {
                api.logging().logToOutput("[RegistrationService] Found correlation to deactivate: " + correlation.getDisplayInfo());
                
                // Send deregistration request to server
                try {
                    deregisterFromServer(correlation).join();
                } catch (Exception e) {
                    api.logging().logToError("[RegistrationService] Failed to deregister from server: " + e.getMessage());
                }
                
                correlation.setActive(false);
                persistCorrelations();
                api.logging().logToOutput("[RegistrationService] Successfully deactivated correlation: " + correlationId);
                return;
            }
        }
        
        api.logging().logToOutput("[RegistrationService] No correlation found with ID: " + correlationId);
    }
    
    /**
     * Deregister correlation from server
     * Based on InteractshClient.deregister() method
     */
    public CompletableFuture<Boolean> deregisterFromServer(Correlation correlation) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                api.logging().logToOutput("[RegistrationService] Deregistering correlation from server: " + correlation.getDisplayInfo());
                
                // Create deregistration payload
                JSONObject deregisterData = new JSONObject();
                deregisterData.put("correlation-id", correlation.getCorrelationId());
                deregisterData.put("secret-key", correlation.getSecretKey());
                
                String requestBody = deregisterData.toString();
                
                // Parse server URL
                URI uri = URI.create(correlation.getServerUrl());
                String host = uri.getHost();
                int port = uri.getPort();
                if (port == -1) {
                    port = "https".equals(uri.getScheme()) ? 443 : 80;
                }
                boolean isHttps = "https".equals(uri.getScheme());
                
                // Build HTTP request
                StringBuilder rawRequestBuilder = new StringBuilder();
                rawRequestBuilder.append("POST /deregister HTTP/1.1\r\n")
                        .append("Host: ").append(host).append("\r\n")
                        .append("User-Agent: RequestBin-Burp-Extension/1.1\r\n")
                        .append("Content-Type: application/json\r\n")
                        .append("Content-Length: ").append(requestBody.length()).append("\r\n");
                
                // Add authorization header if server has token
                if (correlation.getServerToken() != null && !correlation.getServerToken().isEmpty()) {
                    rawRequestBuilder.append("Authorization: ").append(correlation.getServerToken()).append("\r\n");
                }
                
                rawRequestBuilder.append("Connection: close\r\n\r\n").append(requestBody);
                
                String rawRequest = rawRequestBuilder.toString();
                
                // Create Burp HTTP request
                HttpService httpService = HttpService.httpService(host, port, isHttps);
                HttpRequest httpRequest = HttpRequest.httpRequest(httpService, rawRequest);
                
                api.logging().logToOutput("[RegistrationService] Sending deregistration request to: " + correlation.getServerUrl() + "/deregister");
                api.logging().logToOutput("[RegistrationService] Deregistration payload - correlationId: " + correlation.getCorrelationId());
                
                long startTime = System.currentTimeMillis();
                HttpResponse response = api.http().sendRequest(httpRequest).response();
                long responseTime = System.currentTimeMillis() - startTime;
                
                api.logging().logToOutput("[RegistrationService] Deregistration response received in " + responseTime + "ms - Status: " + response.statusCode());
                
                if (response.statusCode() == 200) {
                    api.logging().logToOutput("[RegistrationService] Successfully deregistered correlation: " + correlation.getCorrelationId());
                    return true;
                } else {
                    String errorMsg = "Deregistration failed with status code: " + response.statusCode();
                    String responseBody = response.bodyToString();
                    api.logging().logToOutput("[RegistrationService] Error response body: " + responseBody);
                    
                    try {
                        JSONObject errorData = new JSONObject(responseBody);
                        errorMsg = errorData.optString("error", errorMsg);
                    } catch (Exception ignored) {}
                    
                    api.logging().logToError("[RegistrationService] Deregistration failed: " + errorMsg);
                    return false;
                }
                
            } catch (Exception e) {
                api.logging().logToError("[RegistrationService] Error during deregistration: " + e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * Clear all correlations
     */
    public void clearAllCorrelations() {
        api.logging().logToOutput("[RegistrationService] Clearing all correlations (current count: " + correlations.size() + ")");
        correlations.clear();
        persistCorrelations();
        api.logging().logToOutput("[RegistrationService] Successfully cleared all correlations");
    }
    
    /**
     * Update correlation and persist immediately
     * Use this for important updates like AES key
     */
    public void updateCorrelation(String serverId, Correlation updatedCorrelation) {
        if (updatedCorrelation != null && serverId != null) {
            api.logging().logToOutput("[RegistrationService] Updating correlation for server: " + serverId);
            correlations.put(serverId, updatedCorrelation);
            persistCorrelations();
            api.logging().logToOutput("[RegistrationService] Successfully updated and persisted correlation: " + updatedCorrelation.getDisplayInfo());
        }
    }
    
    /**
     * Update AES key for a correlation and persist
     */
    public void updateAESKey(String serverId, String aesKey) {
        Correlation correlation = correlations.get(serverId);
        if (correlation != null) {
            api.logging().logToOutput("[RegistrationService] Updating AES key for correlation: " + correlation.getDisplayInfo());
            correlation.setAesKey(aesKey);
            correlation.updateLastUsed();
            persistCorrelations();
            api.logging().logToOutput("[RegistrationService] Successfully updated AES key and persisted for server: " + serverId);
        } else {
            api.logging().logToError("[RegistrationService] Cannot update AES key - correlation not found for server: " + serverId);
        }
    }
    
    /**
     * Get correlation statistics for debugging
     */
    public String getCorrelationStats() {
        int totalCorrelations = correlations.size();
        int activeCorrelations = (int) correlations.values().stream().filter(Correlation::isActive).count();
        int inactiveCorrelations = totalCorrelations - activeCorrelations;
        
        StringBuilder stats = new StringBuilder();
        stats.append("Correlation Statistics:\n");
        stats.append("  Total: ").append(totalCorrelations).append("\n");
        stats.append("  Active: ").append(activeCorrelations).append("\n");
        stats.append("  Inactive: ").append(inactiveCorrelations).append("\n");
        stats.append("  Details:\n");
        
        for (Correlation correlation : correlations.values()) {
            stats.append("    - ").append(correlation.getDisplayInfo())
                 .append(" (").append(correlation.hasAesKey() ? "hasAES" : "noAES").append(")")
                 .append(" lastUsed=").append(correlation.getLastUsedAt())
                 .append("\n");
        }
        
        return stats.toString();
    }
    
    /**
     * Load persisted correlations from Burp preferences
     */
    private void loadPersistedCorrelations() {
        try {
            String correlationsJson = api.persistence().preferences().getString(PREF_CORRELATIONS);
            if (correlationsJson != null && !correlationsJson.isEmpty()) {
                api.logging().logToOutput("[RegistrationService] Loading persisted correlations from preferences...");
                
                JSONObject jsonObject = new JSONObject(correlationsJson);
                JSONArray correlationsArray = jsonObject.getJSONArray("correlations");
                
                int loadedCount = 0;
                for (int i = 0; i < correlationsArray.length(); i++) {
                    JSONObject correlationJson = correlationsArray.getJSONObject(i);
                    Correlation correlation = parseCorrelationFromJSON(correlationJson);
                    
                    if (correlation != null) {
                        correlations.put(correlation.getServerId(), correlation);
                        loadedCount++;
                        api.logging().logToOutput("[RegistrationService] Loaded correlation: " + correlation.getDisplayInfo());
                    }
                }
                
                api.logging().logToOutput("[RegistrationService] Successfully loaded " + loadedCount + " persisted correlations");
            } else {
                api.logging().logToOutput("[RegistrationService] No persisted correlations found - starting fresh");
            }
        } catch (Exception e) {
            api.logging().logToError("[RegistrationService] Error loading persisted correlations: " + e.getMessage());
            // Continue with empty correlations map on error
            correlations.clear();
        }
    }
    
    /**
     * Persist correlations to Burp preferences
     */
    private void persistCorrelations() {
        try {
            api.logging().logToOutput("[RegistrationService] Persisting " + correlations.size() + " correlations to preferences...");
            
            JSONArray correlationsArray = new JSONArray();
            
            for (Correlation correlation : correlations.values()) {
                if (correlation != null && correlation.isActive()) {
                    JSONObject correlationJson = correlationToJSON(correlation);
                    correlationsArray.put(correlationJson);
                }
            }
            
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("correlations", correlationsArray);
            jsonObject.put("lastSaved", java.time.Instant.now().toString());
            
            api.persistence().preferences().setString(PREF_CORRELATIONS, jsonObject.toString());
            api.logging().logToOutput("[RegistrationService] Successfully persisted " + correlationsArray.length() + " correlations");
            
        } catch (Exception e) {
            api.logging().logToError("[RegistrationService] Error persisting correlations: " + e.getMessage());
        }
    }
    
    /**
     * Convert Correlation to JSON
     */
    private JSONObject correlationToJSON(Correlation correlation) {
        JSONObject json = new JSONObject();
        json.put("correlationId", correlation.getCorrelationId());
        json.put("secretKey", correlation.getSecretKey());
        json.put("publicKey", correlation.getPublicKey());
        json.put("privateKey", correlation.getPrivateKey());
        json.put("aesKey", correlation.getAesKey() != null ? correlation.getAesKey() : "");
        json.put("serverId", correlation.getServerId());
        json.put("serverUrl", correlation.getServerUrl());
        json.put("serverToken", correlation.getServerToken() != null ? correlation.getServerToken() : "");
        json.put("isActive", correlation.isActive());
        json.put("increment", correlation.getIncrement());
        json.put("createdAt", correlation.getCreatedAt().toString());
        json.put("lastUsedAt", correlation.getLastUsedAt().toString());
        return json;
    }
    
    /**
     * Parse Correlation from JSON
     */
    private Correlation parseCorrelationFromJSON(JSONObject json) {
        try {
            Correlation correlation = new Correlation();
            correlation.setCorrelationId(json.getString("correlationId"));
            correlation.setSecretKey(json.getString("secretKey"));
            correlation.setPublicKey(json.getString("publicKey"));
            correlation.setPrivateKey(json.getString("privateKey"));
            correlation.setAesKey(json.optString("aesKey", ""));
            correlation.setServerId(json.getString("serverId"));
            correlation.setServerUrl(json.getString("serverUrl"));
            correlation.setServerToken(json.optString("serverToken", ""));
            correlation.setActive(json.optBoolean("isActive", true));
            correlation.setIncrement(json.optInt("increment", 0));
            
            String createdAtStr = json.optString("createdAt");
            if (createdAtStr != null && !createdAtStr.isEmpty()) {
                correlation.setCreatedAt(java.time.Instant.parse(createdAtStr));
            }
            
            String lastUsedAtStr = json.optString("lastUsedAt");
            if (lastUsedAtStr != null && !lastUsedAtStr.isEmpty()) {
                correlation.setLastUsedAt(java.time.Instant.parse(lastUsedAtStr));
            }
            
            return correlation;
        } catch (Exception e) {
            api.logging().logToError("[RegistrationService] Error parsing correlation from JSON: " + e.getMessage());
            return null;
        }
    }
}