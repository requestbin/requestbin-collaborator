package burp.models;

import java.time.Instant;
import java.util.Objects;

/**
 * Model representing a RequestBin server
 * Based on BinServer model from requestbin.saas
 */
public class BinServer {
    private String serverId;
    private String name;
    private String serverUrl;
    private String serverToken;
    private String description;
    private boolean isShared;
    private boolean isDefault;
    private HealthStatus healthStatus;
    private Long responseTime;
    private Instant lastHealthCheck;
    private Instant lastUsedAt;
    private boolean isLastUsed;

    public enum HealthStatus {
        HEALTHY("healthy"),
        UNHEALTHY("unhealthy"), 
        UNKNOWN("unknown");

        private final String value;

        HealthStatus(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static HealthStatus fromString(String value) {
            for (HealthStatus status : HealthStatus.values()) {
                if (status.value.equals(value)) {
                    return status;
                }
            }
            return UNKNOWN;
        }
    }

    // Constructors
    public BinServer() {}

    public BinServer(String serverId, String name, String serverUrl, String serverToken) {
        this.serverId = serverId;
        this.name = name;
        this.serverUrl = serverUrl;
        this.serverToken = serverToken;
        this.healthStatus = HealthStatus.UNKNOWN;
        this.isShared = false;
        this.isDefault = false;
    }

    // Getters and Setters
    public String getServerId() { return serverId; }
    public void setServerId(String serverId) { this.serverId = serverId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getServerUrl() { return serverUrl; }
    public void setServerUrl(String serverUrl) { this.serverUrl = serverUrl; }

    public String getServerToken() { return serverToken; }
    public void setServerToken(String serverToken) { this.serverToken = serverToken; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isShared() { return isShared; }
    public void setShared(boolean shared) { isShared = shared; }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }

    public HealthStatus getHealthStatus() { return healthStatus; }
    public void setHealthStatus(HealthStatus healthStatus) { this.healthStatus = healthStatus; }

    public Long getResponseTime() { return responseTime; }
    public void setResponseTime(Long responseTime) { this.responseTime = responseTime; }

    public Instant getLastHealthCheck() { return lastHealthCheck; }
    public void setLastHealthCheck(Instant lastHealthCheck) { this.lastHealthCheck = lastHealthCheck; }

    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }

    public boolean isLastUsed() { return isLastUsed; }
    public void setLastUsed(boolean lastUsed) { isLastUsed = lastUsed; }

    // Utility methods
    public String getDisplayName() {
        if (description != null && !description.isEmpty()) {
            return name + " - " + description;
        }
        return name;
    }

    public String getHealthStatusDisplay() {
        switch (healthStatus) {
            case HEALTHY: return "✓ Healthy";
            case UNHEALTHY: return "✗ Unhealthy"; 
            case UNKNOWN: default: return "? Unknown";
        }
    }

    public boolean isHealthy() {
        return healthStatus == HealthStatus.HEALTHY;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BinServer binServer = (BinServer) o;
        return Objects.equals(serverId, binServer.serverId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serverId);
    }

    @Override
    public String toString() {
        return String.format("BinServer{serverId='%s', name='%s', serverUrl='%s', isShared=%s, healthStatus=%s}", 
                serverId, name, serverUrl, isShared, healthStatus);
    }
}