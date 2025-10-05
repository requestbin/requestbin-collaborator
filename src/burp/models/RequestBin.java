package burp.models;

import java.time.Instant;
import java.util.Objects;

/**
 * Model representing a RequestBin instance
 * Based on Bin model from requestbin.saas
 */
public class RequestBin {
    private String binId;
    private String name;
    private String uniqueId;
    private String url;
    private String note;
    private String serverId;
    private String serverUrl;
    private String correlationId;
    private boolean isActive;
    private Instant createdAt;
    private Instant lastInteractionAt;
    private int interactionCount;

    // Constructors
    public RequestBin() {
        this.isActive = true;
        this.createdAt = Instant.now();
        this.interactionCount = 0;
    }

    public RequestBin(String binId, String name, String uniqueId, String url) {
        this();
        this.binId = binId;
        this.name = name;
        this.uniqueId = uniqueId;
        this.url = url;
    }

    // Static factory method for creating bins
    public static RequestBin createBin(String binId, String name, String uniqueId, String url, 
                                     String serverId, String serverUrl, String correlationId) {
        RequestBin bin = new RequestBin(binId, name, uniqueId, url);
        bin.setServerId(serverId);
        bin.setServerUrl(serverUrl);
        bin.setCorrelationId(correlationId);
        return bin;
    }

    // Getters and Setters
    public String getBinId() { return binId; }
    public void setBinId(String binId) { this.binId = binId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUniqueId() { return uniqueId; }
    public void setUniqueId(String uniqueId) { this.uniqueId = uniqueId; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getServerId() { return serverId; }
    public void setServerId(String serverId) { this.serverId = serverId; }

    public String getServerUrl() { return serverUrl; }
    public void setServerUrl(String serverUrl) { this.serverUrl = serverUrl; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastInteractionAt() { return lastInteractionAt; }
    public void setLastInteractionAt(Instant lastInteractionAt) { this.lastInteractionAt = lastInteractionAt; }

    public int getInteractionCount() { return interactionCount; }
    public void setInteractionCount(int interactionCount) { this.interactionCount = interactionCount; }

    // Utility methods
    public String getDisplayName() {
        if (note != null && !note.trim().isEmpty()) {
            return String.format("%s (%s)", name, note);
        }
        return name;
    }

    public String getType() {
        return "Server";
    }

    public String getStatus() {
        return isActive ? "Active" : "Inactive";
    }

    public void incrementInteractionCount() {
        this.interactionCount++;
        this.lastInteractionAt = Instant.now();
    }

    public String getShortUrl() {
        if (url == null) return "";
        if (url.length() <= 50) return url;
        return url.substring(0, 47) + "...";
    }

    public boolean hasRecentActivity() {
        if (lastInteractionAt == null) return false;
        return lastInteractionAt.isAfter(Instant.now().minusSeconds(300)); // 5 minutes
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RequestBin that = (RequestBin) o;
        return Objects.equals(binId, that.binId) || Objects.equals(uniqueId, that.uniqueId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(binId, uniqueId);
    }

    @Override
    public String toString() {
        return String.format("RequestBin{binId='%s', name='%s', uniqueId='%s', url='%s', serverId='%s', active=%s}", 
                binId, name, uniqueId, url, serverId, isActive);
    }
}