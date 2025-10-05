# Development Guide

## 🎯 Overview

This document provides comprehensive development information for RequestBin Collaborator, a professional Burp Suite extension designed for advanced OOB (Out-of-Band) testing with RequestBin.net integration.

---

## 🏗️ Architecture

### **Core Components**

#### **Models Layer**
- **`BinServer.java`** - Server representations (RequestBin.net, OAST Pro, custom Interactsh)
- **`RequestBin.java`** - Individual bin instances with metadata and interaction tracking
- **`Correlation.java`** - Cryptographic correlation management for secure server communication

#### **Services Layer**
- **`BinManager.java`** - Orchestrates all bin operations and service coordination
- **`BinService.java`** - Core bin CRUD operations and persistence
- **`ServerService.java`** - Server discovery, health checks, and API integration
- **`RegistrationService.java`** - Correlation registration and cryptographic key management
- **`PollingService.java`** - Real-time interaction polling with RequestBin.net servers

#### **UI Components**
- **`InteractshTab.java`** - Main extension interface with tab management
- **`BinTab.java`** - Individual bin monitoring interface with interaction display
- **`BinManagerPanel.java`** - Bin management dashboard
- **`CreateBinDialog.java`** - Modal dialog for creating new bins

---

## 🔧 Build System

### **Maven Profiles**

The project uses Maven profiles for different build configurations:

#### **Development Profile** (Default)
```bash
mvn clean package
# or explicitly
mvn clean package -P dev
```
- **Debug logging**: Enabled
- **File size**: ~305KB
- **Use case**: Development, testing, debugging

#### **Production Profile**
```bash
mvn clean package -P prod
```
- **Debug logging**: Disabled (conditional compilation)
- **File size**: ~295KB (optimized)
- **Use case**: Release builds, production deployment

#### **Docker Build** (Recommended)
```dockerfile
FROM maven:3.9.4-openjdk-17-slim AS builder
COPY . /app
WORKDIR /app
RUN mvn clean package -P prod
```

### **Conditional Debugging System**

Debug logging is implemented with conditional compilation using Maven resource filtering:

```java
// Debug logging that gets compiled out in production
if (DebugMarker.DEBUG_ENABLED) {
    api.logging().logToOutput("[DEBUG] Processing interaction: " + entry.uid);
}
```

**Configuration Files:**
- `src/main/resources-dev/burp/util/DebugMarker.java` - Debug enabled
- `src/main/resources-prod/burp/util/DebugMarker.java` - Debug disabled

---

## 📊 Data Flow Architecture

### **Interaction Processing Pipeline**

```
1. PollingService.pollForInteractions()
   ├── HTTP Request to server /poll endpoint
   ├── AES key extraction and decryption
   └── Encrypted data array processing

2. Data Decryption & Parsing
   ├── decryptData() - AES decryption of interaction data
   ├── InteractshEntry creation from JSON
   └── Protocol-specific parsing (HTTP/DNS/SMTP/etc.)

3. Storage & UI Updates
   ├── saveInteractionToStorage() - Persistent local storage
   ├── BinManager.handleNewInteraction()
   └── UI updates in BinTab with real-time display
```

### **Storage System**

**Local Persistence:**
- **Location**: `~/.requestbin-collaborator/`
- **Format**: Per-bin JSON files (`interactions-{binId}.json`)
- **Structure**: Compatible with RequestBin.net web format

**Burp Suite Preferences:**
- **Bins**: `requestbin.bins` - Serialized bin configurations
- **Correlations**: `requestbin.correlations` - Encrypted correlation data
- **Servers**: `requestbin.servers` - Cached server list with health status

---

## 🔐 Security Implementation

### **Cryptographic Flow**

#### **Registration Process**
1. **Key Generation**: RSA key pair + correlation ID generation
2. **Server Registration**: POST `/register` with public key
3. **Correlation Storage**: Encrypted storage in Burp preferences

#### **Polling Security**
1. **AES Key Exchange**: Server provides encrypted AES key using RSA public key
2. **Data Decryption**: Interactions encrypted with AES for secure transport
3. **Key Management**: Automatic key rotation and secure storage

#### **Deregistration**
1. **Clean Shutdown**: HTTP deregistration calls to server `/deregister`
2. **Key Cleanup**: Secure deletion of correlation data
3. **Session Termination**: Proper cleanup when bins are deleted

```java
// Example: Secure registration flow
CryptoUtils.RegistrationParams params = CryptoUtils.generateRegistrationParams("burp-user");
JSONObject registerData = new JSONObject();
registerData.put("public-key", Base64.getEncoder().encodeToString(params.getPublicKey().getBytes()));
registerData.put("secret-key", params.getSecretKey());
registerData.put("correlation-id", params.getCorrelationId());
```

---

## 🎨 UI/UX Implementation

### **Modern Interface Design**

#### **Tab-Based Architecture**
- **Welcome Screen**: First-time user onboarding with RequestBin.net promotion
- **Bin Tabs**: Individual monitoring interfaces for each bin
- **Management Panel**: Centralized bin creation and management

#### **Real-Time Features**
- **Live Interaction Display**: Instant updates with protocol filtering
- **Unread Counters**: Visual indicators in tab titles
- **Toast Notifications**: Non-intrusive status updates
- **Manual Refresh**: On-demand polling with visual feedback

#### **Empty State Handling**
- **Guided Onboarding**: Professional welcome screens
- **Contextual Help**: Inline tips and RequestBin.net promotion
- **Clear Call-to-Actions**: Strategic placement of upgrade prompts

### **RequestBin.net Integration Points**

1. **Branding Elements**
   - Powered-by attribution in control panels
   - Clickable links to RequestBin.net with UTM tracking
   - Professional styling matching RequestBin.net aesthetics

2. **Promotional Content**
   - Feature comparison highlighting RequestBin.net advantages
   - Strategic placement in empty states and welcome screens
   - Clear value proposition for cloud-based features

---

## 🐛 Debugging & Troubleshooting

### **Debug Categories**

#### **1. Response Processing**
```
[DEBUG] Received response body length: 1247
[DEBUG] Response JSON parsed successfully, keys: aes_key, data, timestamp
[DEBUG] AES key extracted, length: 44
```

#### **2. Cryptographic Operations**
```
[DEBUG] Decrypting AES key - Encrypted length: 256
[DEBUG] AES key decryption successful - Key length: 32
[DEBUG] Data decryption successful - Result length: 523
```

#### **3. Interaction Processing**
```
[DEBUG] Creating InteractshEntry from event: {"protocol":"http",...}
[DEBUG] Entry parsed - Protocol: http, UID: abc123, Address: 192.168.1.100
```

#### **4. Storage Operations**
```
[DEBUG] Saving interaction to storage: /home/user/.requestbin-collaborator/interactions-xyz.json
[DEBUG] Storage write successful - File size: 15KB
```

### **Performance Monitoring**

- **Polling Intervals**: Optimized 30-second intervals with manual refresh capability
- **Memory Management**: Bounded interaction lists with automatic cleanup
- **UI Responsiveness**: Background processing with SwingUtilities threading
- **Storage Efficiency**: Throttled writes with batch updates

---

## 🧪 Testing Strategy

### **Manual Testing Checklist**

#### **Bin Management**
- [ ] Create bins with different server types
- [ ] Switch between multiple bins
- [ ] Delete bins and verify cleanup
- [ ] Test persistence across Burp restarts

#### **Interaction Monitoring**
- [ ] Verify real-time interaction display
- [ ] Test protocol filtering (HTTP/DNS/SMTP)
- [ ] Check unread counter functionality
- [ ] Validate manual refresh operations

#### **RequestBin.net Integration**
- [ ] Test server selection and health checks
- [ ] Verify RequestBin.net authentication
- [ ] Check promotional link functionality
- [ ] Validate branding elements

#### **Error Handling**
- [ ] Network connectivity issues
- [ ] Invalid server configurations
- [ ] Malformed interaction data
- [ ] Storage permission problems

---

## 🚀 Deployment & Release

### **Release Process**

1. **Version Update**: Update version in `pom.xml` and documentation
2. **Production Build**: `mvn clean package -P prod`
3. **Testing**: Comprehensive testing with production JAR
4. **Documentation**: Update README.md and CHANGELOG
5. **GitHub Release**: Create release with JAR attachments

### **Distribution Files**

| File | Size | Dependencies | Use Case |
|------|------|-------------|----------|
| **`collaborator-1.1-jar-with-dependencies.jar`** | 399KB | ✅ All included | **🚀 Release/Distribution** |
| **`collaborator-1.1.jar`** | 141KB | ❌ External required | 🔧 Development/Integration |

**📦 For GitHub Releases**: Always use `collaborator-1.1-jar-with-dependencies.jar`
- **Complete Package**: Includes JSON, crypto, and all required libraries
- **Plug & Play**: Users only need to download one file
- **Cross-Platform**: Works on all Burp Suite installations
- **Enterprise Ready**: No classpath configuration required

### **Deployment Verification**

```bash
# Verify JAR contents
jar -tf collaborator-1.1-jar-with-dependencies.jar | grep -E "(BurpExtender|InteractshTab)"

# Check debug compilation status
strings collaborator-1.1-jar-with-dependencies.jar | grep "DEBUG_ENABLED"
```

---

## 📚 API Integration

### **RequestBin.net API Endpoints**

#### **Server Discovery**
```http
GET https://requestbin.net/api/servers
Authorization: Bearer <token>
```

#### **Bin Operations**
```http
POST https://requestbin.net/api/bins
{
  "name": "Test Bin",
  "description": "Security testing"
}
```

#### **Interaction Polling**
```http
GET https://server.requestbin.net/poll?id=<correlation-id>&secret=<secret-key>
Authorization: Bearer <token>
```

### **Custom Server Support**

The extension supports any Interactsh-compatible server:

```java
BinServer customServer = new BinServer(
    "custom-server",
    "https://interactsh.example.com",
    "Custom Interactsh Server",
    null, // No auth token
    "Custom deployment for internal testing"
);
```

---

## 🔗 Integration Points

### **RequestBin.net Ecosystem**

1. **Web Platform**: Seamless data synchronization
2. **API Services**: Real-time polling and interaction management
3. **Analytics Engine**: Advanced request pattern analysis
4. **Team Collaboration**: Shared bins and reporting

### **Burp Suite Integration**

1. **Extension API**: Full Burp Suite extension interface compliance
2. **HTTP Service**: Native Burp HTTP handling for all server communication
3. **UI Components**: Consistent look and feel with Burp Suite themes
4. **Preferences**: Integration with Burp's settings and persistence system

---

## 📈 Performance Optimization

### **Polling Efficiency**
- **Smart Intervals**: Adaptive polling based on activity
- **Background Processing**: Non-blocking UI updates
- **Connection Pooling**: Efficient HTTP connection management
- **Data Throttling**: Batched storage operations

### **Memory Management**
- **Bounded Collections**: Automatic cleanup of old interactions
- **Weak References**: Proper garbage collection support
- **Resource Cleanup**: Explicit connection and thread cleanup

### **UI Responsiveness**
- **SwingUtilities Threading**: Proper EDT usage for UI updates
- **Progressive Loading**: Lazy loading of large interaction sets
- **Efficient Rendering**: Optimized table models and renderers

---

## 🎯 Future Enhancements

### **Planned Features**
- **Enhanced Analytics**: Integration with RequestBin.net's advanced analytics
- **Custom Payloads**: Template system for common OOB payloads
- **Export Functionality**: Professional reporting and data export
- **Team Features**: Collaborative testing with shared bins

### **RequestBin.net Synergies**
- **Deeper Integration**: Real-time synchronization with web platform
- **Premium Features**: Access to RequestBin.net premium functionality
- **Mobile Support**: Companion mobile app integration
- **Enterprise SSO**: Corporate authentication integration

---

<div align="center">

**Contributing to RequestBin Collaborator**

*Help us build the future of OOB testing tools*

[**Submit Issues →**](https://github.com/requestbin/requestbin-collaborator/issues) | [**Join RequestBin.net →**](https://requestbin.net/signup)

</div>