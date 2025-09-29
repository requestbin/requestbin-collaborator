# RequestBin Collaborator

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)]() 
[![Java](https://img.shields.io/badge/java-17%2B-blue.svg)]()
[![License](https://img.shields.io/badge/license-MIT-green.svg)]()
[![RequestBin.net](https://img.shields.io/badge/powered%20by-RequestBin.net-orange.svg)](https://requestbin.net)

**🚀 Professional Out-of-Band Testing Extension for Burp Suite**

RequestBin Collaborator is a powerful Burp Suite extension designed to enhance your security testing capabilities with advanced OOB (Out-of-Band) interaction monitoring. Built to complement and extend beyond traditional Burp Collaborator functionality, this extension provides seamless integration with RequestBin.net services and self-hosted Interactsh servers.

![RequestBin Demo](assets/interactsh-demo.gif)

---

## 🌟 What Makes RequestBin Collaborator Different

**RequestBin Collaborator** represents a significant evolution from existing OOB testing tools, developed specifically to serve the growing RequestBin.net community while maintaining full compatibility with the broader security testing ecosystem.

### **🎯 Built for RequestBin.net Users**
- **Seamless Integration**: Designed specifically to work with [RequestBin.net](https://requestbin.net) infrastructure
- **Enhanced Performance**: Optimized polling algorithms for faster interaction detection
- **Professional UI**: Refined interface matching modern Burp Suite aesthetics
- **Community Driven**: Actively maintained and enhanced based on user feedback

### **🔄 Evolution from Open Source Foundations**
This project builds upon the excellent foundation laid by the [interactsh-collaborator](https://github.com/wdahlenburg/interactsh-collaborator) project and incorporates community contributions from [@TheArqsz's revised version](https://github.com/TheArqsz/interactsh-collaborator-rev). We deeply respect and acknowledge these contributions while taking the project in a new direction focused on:

- **Professional reliability** for enterprise security teams
- **Enhanced debugging** and troubleshooting capabilities  
- **Streamlined workflow** integration with RequestBin.net services
- **Advanced logging** and interaction analysis features

---

## ✨ Key Features

### **🔥 Core Functionality**
- 🌐 **Dynamic Domain Generation**: Create unique subdomains for comprehensive OOB testing
- 📊 **Multi-Protocol Support**: Monitor DNS, HTTP, HTTPS, SMTP interactions
- 🔒 **Secure Communication**: AES/RSA encryption for all server communications  
- ⚡ **Real-time Monitoring**: Instant notification of incoming interactions
- 🎛️ **Flexible Configuration**: Support for custom Interactsh servers and RequestBin.net

### **🚀 Advanced Features** 
- 🔄 **Smart Polling**: Optimized polling intervals with manual refresh capability
- 📋 **Rich Data Display**: Built-in HTTP request/response viewer with syntax highlighting
- 🎯 **Protocol Filtering**: Collaborator-style filtering for different interaction types
- 📈 **Unread Counter**: Visual indicators for new interactions in tab title
- 🧹 **Session Management**: Regenerate sessions and clear logs with single clicks
- 📱 **Toast Notifications**: Non-intrusive alerts for new interactions

### **🛠 Developer & Enterprise Features**
- 🐛 **Advanced Debug Mode**: Comprehensive logging with build-time configuration
- 🏗️ **Dual Build Modes**: Development (debug enabled) vs Production (optimized) builds  
- 🔍 **Detailed Tracing**: Step-by-step interaction processing logs
- ⚙️ **Performance Monitoring**: Built-in metrics for polling and processing efficiency
- 🎯 **Error Handling**: Graceful error recovery with detailed diagnostic information

---

## 🏗 Build System & Development

### **Quick Build**

**Development Build (Debug Enabled):**
```powershell
mvn clean package -P dev
# or simply (dev is default profile)
mvn clean package  
```

**Production Build (Optimized):**
```powershell
mvn clean package -P prod
```

**Docker Build (Recommended):**
```bash
docker build --output ./build-output .
```

### **📊 Build Modes Explained**

| Build Mode | Debug Logging | File Size | Use Case | Performance |
|------------|---------------|-----------|----------|-------------|
| **Development** | ✅ Enabled | ~305KB | Debugging, Testing | Standard |
| **Production** | ❌ Disabled | ~295KB | Deployment, Release | Optimized |

**Generated Files:**
- `collaborator-1.1.jar` - Basic JAR
- `collaborator-1.1-jar-with-dependencies.jar` - Complete JAR for Burp installation

---

## 📦 Installation & Setup

### **System Requirements**
- ☑️ **Java JDK 17+** (recommended for Burp Suite compatibility)  
- ☑️ **Apache Maven 3.6+** (for building from source)
- ☑️ **Burp Suite Professional/Community** 
- 🌐 **Internet Connection** (for RequestBin.net or Interactsh server access)

### **Installing into Burp Suite**

1. **Download** the latest `collaborator-1.1-jar-with-dependencies.jar` from releases
2. **Open Burp Suite** → **Extensions** → **Installed**  
3. **Click Add** → **Extension type**: Java
4. **Select** the JAR file → **Click Next**
5. **Verify** the "RequestBin" tab appears in Burp Suite

### **🔧 Configuration**

1. **Navigate** to the **RequestBin** tab in Burp Suite
2. **Click Configuration** to set up your server:
   - **RequestBin.net**: Use default settings (recommended)
   - **Self-hosted**: Configure your Interactsh server URL
   - **Custom**: Advanced server configuration options
3. **Test Connection** to verify setup
4. **Start Testing** with the "Copy URL to clipboard" button

---

## 🚀 Usage Guide

### **Basic Workflow**
1. **Generate Payload**: Click "Copy URL to clipboard" to get your unique testing domain
2. **Insert in Tests**: Use the domain in your security tests (XSS, SSRF, XXE, etc.)
3. **Monitor Results**: Watch the interactions table for real-time results
4. **Analyze Data**: Click entries to view detailed HTTP requests/responses
5. **Filter & Export**: Use built-in filtering and export capabilities

### **Advanced Features**
- **Manual Refresh**: Force immediate polling with the "Poll Now" button
- **Session Reset**: Generate new domains with "Regenerate Session" 
- **Clear History**: Remove old interactions with "Clear Log"
- **Protocol Filtering**: Focus on specific interaction types (DNS, HTTP, SMTP)
- **Detailed Analysis**: Examine raw requests and responses in built-in viewer

---

## 🐛 Debug & Troubleshooting  

### **Debug Logging System**

RequestBin Collaborator includes a sophisticated debug system with **build-time configuration**:

**Enable Debug Mode:**
```bash
# Development build (debug automatically enabled)
mvn clean package -P dev

# Runtime override for production builds  
java -Dinteractsh.debug=true -jar burpsuite.jar
```

### **📋 Debug Log Categories**

| Category | Description | Example Output |
|----------|-------------|----------------|
| **Response Processing** | Server communication logs | `[DEBUG] Received response body length: 1247` |
| **AES Key Decryption** | Encryption/decryption tracing | `[DEBUG] AES key decryption successful - Key length: 32` |
| **Data Processing** | Entry creation and parsing | `[DEBUG] Processing item 1/3, encrypted length: 892` |
| **Protocol Analysis** | HTTP/DNS/SMTP specific logging | `[DEBUG] Processing HTTP entry` |
| **Error Handling** | Exception tracing and recovery | `[DEBUG] Exception in polling: JSONException` |

### **🔍 Common Issues & Solutions**

**Issue 1: Empty Interactions**
```
[DEBUG] Received data array with 0 items  
```
**Solutions:**
- Check network connectivity to server
- Verify polling interval settings  
- Confirm server authentication

**Issue 2: Decryption Errors**  
```
[DEBUG] Exception in polling: BadPaddingException
```
**Solutions:**
- Verify private key configuration
- Check server compatibility
- Validate Base64 encoding

**Issue 3: UI Not Responsive**
```
Extension appears frozen
```
**Solutions:**
- Check Burp Output tab for errors
- Restart extension or regenerate session
- Verify Java version compatibility

---

## 🏗 Development & Contributing

### **Project Structure**
```
requestbin-collaborator/
├── src/
│   ├── burp/
│   │   ├── BurpExtender.java          # Main extension entry point
│   │   ├── gui/
│   │   │   ├── Config.java            # Configuration management
│   │   │   ├── InteractshTab.java     # Main UI tab implementation  
│   │   │   └── ToastNotification.java # Notification system
│   │   ├── listeners/
│   │   │   └── InteractshListener.java # Event handling
│   │   └── util/
│   │       ├── DebugLogger.java       # Conditional debug logging
│   │       └── DebugMarker.java       # Build mode detection
│   ├── interactsh/
│   │   ├── InteractshClient.java      # Server communication API
│   │   └── InteractshEntry.java       # Data model for interactions  
│   └── layout/
│       └── SpringUtilities.java       # UI layout utilities
├── target/                            # Build outputs
├── pom.xml                           # Maven configuration  
├── Dockerfile                        # Container build
└── README.md                        # This file
```

### **🔧 Development Workflow**

**1. Setup Development Environment:**
```powershell
git clone <repository-url>
cd requestbin-collaborator
mvn clean compile    # Verify setup
```

**2. Make Changes:**
- Edit Java files in `src/` directory
- Follow existing code patterns and conventions
- Add debug logging for new features

**3. Build & Test:**
```powershell
mvn clean package -P dev    # Build with debug
# Load JAR into Burp Suite for testing
mvn clean package -P prod   # Build for production
```

**4. Debug & Validate:**
- Check **Burp Output** tab for debug logs
- Test with actual interactions
- Verify UI responsiveness and error handling

### **🤝 Contributing Guidelines**

1. **Fork** the repository
2. **Create** feature branch: `git checkout -b feature/amazing-feature`
3. **Follow** existing code style and patterns
4. **Add** comprehensive debug logging for new features  
5. **Test** thoroughly with both build modes
6. **Update** documentation as needed
7. **Submit** Pull Request with detailed description

---

## 📚 Technical Details

### **Dependencies**
- **Montoya API 2025.8** - Burp Suite Extension API
- **JSON 20250517** - JSON processing and parsing
- **Java XID 1.0.0** - Unique identifier generation
- **Bouncy Castle** - Cryptographic operations (AES/RSA)

### **Compatibility**  
- **Burp Suite**: Professional & Community editions
- **Java**: JDK 17+ (recommended for optimal compatibility)
- **Platforms**: Windows, macOS, Linux
- **Servers**: Interactsh, RequestBin.net, self-hosted instances

### **Performance Characteristics**
- **Polling Frequency**: Configurable (1-300 seconds)
- **Memory Usage**: ~50MB typical, ~100MB peak
- **Network Impact**: Minimal (encrypted polling requests)  
- **UI Responsiveness**: Non-blocking architecture with background processing

---

## 🔗 Resources & Support

- **RequestBin.net**: [https://requestbin.net](https://requestbin.net)
- **Burp Extensions API**: [https://portswigger.net/burp/extender/api/](https://portswigger.net/burp/extender/api/)
- **Montoya API Documentation**: [https://portswigger.github.io/burp-extensions-montoya-api/](https://portswigger.github.io/burp-extensions-montoya-api/)
- **Interactsh Project**: [https://github.com/projectdiscovery/interactsh](https://github.com/projectdiscovery/interactsh)

---

## 📜 License & Acknowledgments

**License**: MIT License - see LICENSE file for details

**Acknowledgments**: 
- Original [interactsh-collaborator](https://github.com/wdahlenburg/interactsh-collaborator) by @wdahlenburg
- Enhanced version [interactsh-collaborator-rev](https://github.com/TheArqsz/interactsh-collaborator-rev) by @TheArqsz  
- [ProjectDiscovery](https://github.com/projectdiscovery) for the Interactsh framework
- Burp Suite team for the excellent extension API

**Developed with ❤️ for the security testing community by RequestBin.net**

---
