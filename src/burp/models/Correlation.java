package burp.models;

import java.time.Instant;

/**
 * Model representing a correlation between a bin and a server
 * Based on Correlation model from requestbin.saas
 */
public class Correlation {
    private String correlationId;
    private String secretKey;
    private String publicKey;
    private String privateKey;
    private String aesKey;
    private String serverId;
    private String serverUrl;
    private String serverToken;
    private boolean isActive;
    private int increment;
    private Instant createdAt;
    private Instant lastUsedAt;

    // Constructors
    public Correlation() {
        this.isActive = true;
        this.increment = 0;
        this.createdAt = Instant.now();
        this.lastUsedAt = Instant.now();
        this.aesKey = ""; // Will be populated during first interaction
    }

    public Correlation(String correlationId, String secretKey, String publicKey, 
                      String privateKey, String serverId, String serverUrl, String serverToken) {
        this();
        this.correlationId = correlationId;
        this.secretKey = secretKey;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.serverId = serverId;
        this.serverUrl = serverUrl;
        this.serverToken = serverToken;
    }

    // Getters and Setters
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }

    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }

    public String getPrivateKey() { return privateKey; }
    public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }

    public String getAesKey() { return aesKey; }
    public void setAesKey(String aesKey) { this.aesKey = aesKey; }

    public String getServerId() { return serverId; }
    public void setServerId(String serverId) { this.serverId = serverId; }

    public String getServerUrl() { return serverUrl; }
    public void setServerUrl(String serverUrl) { this.serverUrl = serverUrl; }

    public String getServerToken() { return serverToken; }
    public void setServerToken(String serverToken) { this.serverToken = serverToken; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public int getIncrement() { return increment; }
    public void setIncrement(int increment) { this.increment = increment; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }

    // Utility methods
    public void updateLastUsed() {
        this.lastUsedAt = Instant.now();
    }

    public void incrementCounter() {
        this.increment++;
    }

    public boolean hasAesKey() {
        return aesKey != null && !aesKey.trim().isEmpty();
    }

    public String getDisplayInfo() {
        return String.format("Correlation{id='%s', server='%s', active=%s}", 
                correlationId != null ? correlationId.substring(0, Math.min(8, correlationId.length())) : "null", 
                serverId, isActive);
    }

    @Override
    public String toString() {
        return String.format("Correlation{correlationId='%s', serverId='%s', serverUrl='%s', active=%s}", 
                correlationId, serverId, serverUrl, isActive);
    }
}