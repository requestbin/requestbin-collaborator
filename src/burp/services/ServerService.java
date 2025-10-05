package burp.services;

import burp.api.montoya.MontoyaApi;
import burp.models.BinServer;
import org.json.JSONArray;
import org.json.JSONObject;

import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing RequestBin servers
 * Based on the servers API from requestbin.saas
 */
public class ServerService {
    private final MontoyaApi api;
    
    private static final String DEFAULT_SERVERS_API = "https://requestbin.net/api/servers";
    private static final String CACHE_KEY_SERVERS = "requestbin.cached.servers";
    private static final String CACHE_KEY_TIMESTAMP = "requestbin.cached.servers.timestamp";
    private static final long CACHE_EXPIRY_MINUTES = 30; // 30 minutes cache

    public ServerService(MontoyaApi api) {
        this.api = api;
    }

    /**
     * Get available servers from RequestBin.net API with caching
     */
    public CompletableFuture<List<BinServer>> getAvailableServers() {
        return CompletableFuture.supplyAsync(() -> {
            // Try to get from cache first
            List<BinServer> cachedServers = getCachedServers();
            if (cachedServers != null) {
                api.logging().logToOutput("Using cached servers (" + cachedServers.size() + " servers)");
                return cachedServers;
            }

            try {
                // Fetch from API
                api.logging().logToOutput("Fetching servers from RequestBin.net API...");
                List<BinServer> servers = fetchServersFromAPI();
                
                if (!servers.isEmpty()) {
                    // Cache the results
                    cacheServers(servers);
                    api.logging().logToOutput("Successfully fetched " + servers.size() + " servers from API");
                    return servers;
                }
                
                // If API failed, return default servers
                api.logging().logToOutput("API returned empty, using default servers");
                return getDefaultServers();
                
            } catch (Exception e) {
                api.logging().logToError("Error fetching servers from API: " + e.getMessage());
                
                // Fallback to cached servers even if expired
                List<BinServer> fallbackServers = getCachedServers(true);
                if (fallbackServers != null) {
                    api.logging().logToOutput("Using expired cached servers as fallback");
                    return fallbackServers;
                }
                
                // Last resort: return default servers
                api.logging().logToOutput("Using default servers as last resort");
                return getDefaultServers();
            }
        });
    }

    /**
     * Fetch servers from RequestBin.net API
     */
    private List<BinServer> fetchServersFromAPI() throws Exception {
        // Parse API URL to get host, port, scheme
        URI uri = URI.create(DEFAULT_SERVERS_API);
        String host = uri.getHost();
        int port = uri.getPort();
        if (port == -1) {
            port = "https".equals(uri.getScheme()) ? 443 : 80;
        }
        boolean isHttps = "https".equals(uri.getScheme());
        String path = uri.getPath();
        
        // Build raw HTTP request string like InteractshClient
        StringBuilder rawRequestBuilder = new StringBuilder();
        rawRequestBuilder.append("GET ").append(path).append(" HTTP/1.1\r\n")
                .append("Host: ").append(host).append("\r\n")
                .append("User-Agent: RequestBin-Burp-Extension/1.1\r\n")
                .append("Accept: application/json\r\n")
                .append("Connection: close\r\n\r\n");
        
        String rawRequest = rawRequestBuilder.toString();
        
        // Create Burp HTTP request
        HttpService httpService = HttpService.httpService(host, port, isHttps);
        HttpRequest httpRequest = HttpRequest.httpRequest(httpService, rawRequest);
        
        HttpResponse response = api.http().sendRequest(httpRequest).response();

        if (response.statusCode() != 200) {
            throw new RuntimeException("API returned status code: " + response.statusCode());
        }

        return parseServersResponse(response.bodyToString());
    }

    /**
     * Parse servers response from API
     */
    private List<BinServer> parseServersResponse(String responseBody) {
        List<BinServer> servers = new ArrayList<>();
        
        try {
            JSONObject jsonResponse = new JSONObject(responseBody);
            JSONArray serversArray = jsonResponse.getJSONArray("servers");

            for (int i = 0; i < serversArray.length(); i++) {
                JSONObject serverJson = serversArray.getJSONObject(i);
                BinServer server = parseServerFromJSON(serverJson);
                if (server != null) {
                    servers.add(server);
                }
            }
        } catch (Exception e) {
            api.logging().logToError("Error parsing servers response: " + e.getMessage());
        }

        return servers;
    }

    /**
     * Parse individual server from JSON
     */
    private BinServer parseServerFromJSON(JSONObject json) {
        try {
            BinServer server = new BinServer();
            
            server.setServerId(json.getString("serverId"));
            server.setName(json.getString("name"));
            server.setServerUrl(json.getString("serverUrl"));
            server.setServerToken(json.optString("serverToken", ""));
            server.setDescription(json.optString("description", ""));
            server.setShared(json.optString("owner", "").equals("0"));
            server.setDefault(json.optBoolean("isDefault", false));
            
            // Parse health status
            String healthStatus = json.optString("healthStatus", "unknown");
            server.setHealthStatus(BinServer.HealthStatus.fromString(healthStatus));
            
            // Parse response time
            if (json.has("responseTime") && !json.isNull("responseTime")) {
                server.setResponseTime(json.getLong("responseTime"));
            }
            
            // Parse last health check
            String lastHealthCheck = json.optString("lastHealthCheck", null);
            if (lastHealthCheck != null && !lastHealthCheck.isEmpty()) {
                server.setLastHealthCheck(Instant.parse(lastHealthCheck));
            }

            return server;
        } catch (Exception e) {
            api.logging().logToError("Error parsing server JSON: " + e.getMessage());
            return null;
        }
    }

    /**
     * Get cached servers if still valid
     */
    private List<BinServer> getCachedServers() {
        return getCachedServers(false);
    }

    /**
     * Get cached servers with option to ignore expiry
     */
    private List<BinServer> getCachedServers(boolean ignoreExpiry) {
        try {
            String timestampStr = api.persistence().preferences().getString(CACHE_KEY_TIMESTAMP);
            String serversJson = api.persistence().preferences().getString(CACHE_KEY_SERVERS);

            if (timestampStr == null || serversJson == null) {
                return null;
            }

            long timestamp = Long.parseLong(timestampStr);
            long currentTime = Instant.now().getEpochSecond();
            
            // Check if cache is still valid (within expiry time)
            if (!ignoreExpiry && (currentTime - timestamp) > (CACHE_EXPIRY_MINUTES * 60)) {
                return null; // Cache expired
            }

            // Parse cached servers
            JSONArray serversArray = new JSONArray(serversJson);
            List<BinServer> servers = new ArrayList<>();
            
            for (int i = 0; i < serversArray.length(); i++) {
                JSONObject serverJson = serversArray.getJSONObject(i);
                BinServer server = parseServerFromJSON(serverJson);
                if (server != null) {
                    servers.add(server);
                }
            }

            return servers;
        } catch (Exception e) {
            api.logging().logToError("Error reading cached servers: " + e.getMessage());
            return null;
        }
    }

    /**
     * Cache servers to Burp preferences
     */
    private void cacheServers(List<BinServer> servers) {
        try {
            JSONArray serversArray = new JSONArray();
            
            for (BinServer server : servers) {
                JSONObject serverJson = new JSONObject();
                serverJson.put("serverId", server.getServerId());
                serverJson.put("name", server.getName());
                serverJson.put("serverUrl", server.getServerUrl());
                serverJson.put("serverToken", server.getServerToken());
                serverJson.put("description", server.getDescription());
                serverJson.put("owner", server.isShared() ? "0" : "user");
                serverJson.put("isDefault", server.isDefault());
                serverJson.put("healthStatus", server.getHealthStatus().getValue());
                
                if (server.getResponseTime() != null) {
                    serverJson.put("responseTime", server.getResponseTime());
                }
                
                if (server.getLastHealthCheck() != null) {
                    serverJson.put("lastHealthCheck", server.getLastHealthCheck().toString());
                }
                
                serversArray.put(serverJson);
            }

            // Save to preferences
            api.persistence().preferences().setString(CACHE_KEY_SERVERS, serversArray.toString());
            api.persistence().preferences().setString(CACHE_KEY_TIMESTAMP, 
                    String.valueOf(Instant.now().getEpochSecond()));

        } catch (Exception e) {
            api.logging().logToError("Error caching servers: " + e.getMessage());
        }
    }

    /**
     * Get default fallback servers
     */
    private List<BinServer> getDefaultServers() {
        List<BinServer> servers = new ArrayList<>();
        
        // RequestBin.net official server
        BinServer requestBin = new BinServer("requestbin-net", "RequestBin.net", 
                "https://requestbin.net", "");
        requestBin.setDescription("Official RequestBin.net server");
        requestBin.setShared(true);
        requestBin.setDefault(true);
        requestBin.setHealthStatus(BinServer.HealthStatus.UNKNOWN);
        servers.add(requestBin);
        
        // OAST Pro
        BinServer oastPro = new BinServer("oast-pro", "OAST Pro", 
                "https://oast.pro", "");
        oastPro.setDescription("Premium Managed OAST server");
        oastPro.setShared(true);
        oastPro.setHealthStatus(BinServer.HealthStatus.UNKNOWN);
        servers.add(oastPro);
        
        // Interactsh public
        BinServer interactsh = new BinServer("interactsh-public", "Interactsh", 
                "https://oast.live", "");
        interactsh.setDescription("Public Interactsh server");
        interactsh.setShared(true);
        interactsh.setHealthStatus(BinServer.HealthStatus.UNKNOWN);
        servers.add(interactsh);

        return servers;
    }

    /**
     * Clear cached servers (force refresh on next request)
     */
    public void clearCache() {
        try {
            api.persistence().preferences().setString(CACHE_KEY_SERVERS, null);
            api.persistence().preferences().setString(CACHE_KEY_TIMESTAMP, null);
            api.logging().logToOutput("Server cache cleared");
        } catch (Exception e) {
            api.logging().logToError("Error clearing server cache: " + e.getMessage());
        }
    }

    /**
     * Perform health check for a specific server
     */
    public CompletableFuture<BinServer.HealthStatus> performHealthCheck(BinServer server) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String healthUrl = server.getServerUrl() + "/health";
                
                // Parse URL to get host, port, scheme
                URI uri = URI.create(healthUrl);
                String host = uri.getHost();
                int port = uri.getPort();
                if (port == -1) {
                    port = "https".equals(uri.getScheme()) ? 443 : 80;
                }
                boolean isHttps = "https".equals(uri.getScheme());
                String path = uri.getPath();
                
                // Build raw HTTP request string like InteractshClient
                StringBuilder rawRequestBuilder = new StringBuilder();
                rawRequestBuilder.append("GET ").append(path).append(" HTTP/1.1\r\n")
                        .append("Host: ").append(host).append("\r\n")
                        .append("User-Agent: RequestBin-Burp-Extension/1.1\r\n")
                        .append("Connection: close\r\n\r\n");
                
                String rawRequest = rawRequestBuilder.toString();
                
                // Create Burp HTTP request
                HttpService httpService = HttpService.httpService(host, port, isHttps);
                HttpRequest httpRequest = HttpRequest.httpRequest(httpService, rawRequest);

                long startTime = System.currentTimeMillis();
                HttpResponse response = api.http().sendRequest(httpRequest).response();
                long responseTime = System.currentTimeMillis() - startTime;

                if (response.statusCode() == 200) {
                    server.setHealthStatus(BinServer.HealthStatus.HEALTHY);
                    server.setResponseTime(responseTime);
                    server.setLastHealthCheck(Instant.now());
                    return BinServer.HealthStatus.HEALTHY;
                } else {
                    server.setHealthStatus(BinServer.HealthStatus.UNHEALTHY);
                    server.setLastHealthCheck(Instant.now());
                    return BinServer.HealthStatus.UNHEALTHY;
                }

            } catch (Exception e) {
                api.logging().logToOutput("Health check failed for " + server.getName() + ": " + e.getMessage());
                server.setHealthStatus(BinServer.HealthStatus.UNHEALTHY);
                server.setLastHealthCheck(Instant.now());
                return BinServer.HealthStatus.UNHEALTHY;
            }
        });
    }
}