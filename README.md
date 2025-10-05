# RequestBin Collaborator

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)]() 
[![Java](https://img.shields.io/badge/java-17%2B-blue.svg)]()
[![License](https://img.shields.io/badge/license-MIT-green.svg)]()
[![RequestBin.net](https://img.shields.io/badge/powered%20by-RequestBin.net-orange.svg)](https://requestbin.net)

**🚀 Professional Out-of-Band Testing Extension for Burp Suite**

RequestBin Collaborator is a powerful Burp Suite extension designed to enhance your security testing capabilities with advanced OOB (Out-of-Band) interaction monitoring. Built to complement [RequestBin.net](https://requestbin.net) services, this extension provides seamless integration with cloud-based request bins and self-hosted Interactsh servers.

![RequestBin Demo](assets/requestbin-demo.gif)

---

## 🌟 Why Choose RequestBin Collaborator?

**RequestBin Collaborator** represents the next evolution in OOB testing tools, specifically designed to serve the growing [RequestBin.net](https://requestbin.net) community while maintaining compatibility with the broader security testing ecosystem.

### **🎯 Built for Modern Security Testing**
- **🌐 RequestBin.net Integration**: Seamlessly connect to [RequestBin.net](https://requestbin.net) for enhanced cloud-based testing
- **⚡ Enhanced Performance**: Optimized polling algorithms and real-time interaction detection
- **🎨 Professional UI**: Refined interface matching modern Burp Suite aesthetics
- **🔧 Enterprise Ready**: Designed for professional security teams and consultants

### **🔄 Evolution of Excellence**
Built upon the solid foundation of the open-source [interactsh-collaborator](https://github.com/wdahlenburg/interactsh-collaborator) project, we've enhanced it with:

- **Professional reliability** for enterprise environments
- **Advanced debugging** and comprehensive logging
- **Streamlined workflows** with RequestBin.net services
- **Modern UI/UX** improvements for better productivity

---

## ✨ Key Features

### **🔥 Core Capabilities**
- 🌐 **Multi-Server Support**: Connect to RequestBin.net, OAST Pro, or custom Interactsh servers
- 📊 **Multi-Protocol Monitoring**: DNS, HTTP, HTTPS, SMTP, LDAP, SMB, FTP interactions
- 🔒 **Enterprise Security**: AES/RSA encryption for secure server communications
- ⚡ **Real-Time Updates**: Instant notifications and automatic polling with manual refresh
- 🎛️ **Flexible Management**: Create, manage, and switch between multiple request bins

### **🚀 Advanced Features**
- 📋 **Rich Data Visualization**: Built-in HTTP request/response viewer with syntax highlighting
- 🎯 **Smart Filtering**: Protocol-based filtering with unread interaction management
- 📈 **Professional UI**: Tab-based interface with interaction counters and status indicators
- 🔄 **Persistent Storage**: Automatic data persistence with cross-session continuity
- 🧹 **Session Management**: Easy bin creation, deletion, and session regeneration

### **🌟 RequestBin.net Integration**
- 🚀 **Cloud-Powered**: Leverage RequestBin.net's global infrastructure for testing
- 📊 **Enhanced Analytics**: Access advanced request analysis on RequestBin.net
- 🔗 **Seamless Workflow**: Direct links to RequestBin.net for extended functionality
- 💼 **Professional Features**: Access premium RequestBin.net features directly from Burp Suite
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

---

## 🚀 Quick Start

### **Installation**

1. **Download** the latest release from [GitHub Releases](https://github.com/requestbin/requestbin-collaborator/releases)
2. **Open Burp Suite** → Extensions → Installed → Add
3. **Select** `collaborator-1.1-jar-with-dependencies.jar`
4. **Navigate** to the "RequestBin Collaborator" tab

### **First Steps**

1. **Create Your First Bin**
   - Click "Create Your First Bin" or use the "+" tab
   - Choose between RequestBin.net (recommended) or custom servers
   - Give your bin a descriptive name

2. **Start Testing**
   - Copy the generated URL from your bin
   - Use it in your security testing payloads
   - Monitor real-time interactions in the extension

3. **Explore Advanced Features**
   - Visit [RequestBin.net](https://requestbin.net) for enhanced analytics
   - Use protocol filtering to focus on specific interaction types
   - Export or share your findings with team members

---

## 🏢 Enterprise & Professional Use

**RequestBin Collaborator** is designed with professional security teams in mind:

### **🎯 Security Consulting**
- Generate unique subdomains for each client engagement
- Professional reporting integration with RequestBin.net
- Persistent data across testing sessions

### **🏗️ Enterprise Security Teams**
- Multi-server support for different environments
- Advanced logging and debugging capabilities
- Team collaboration through RequestBin.net sharing

### **📚 Security Education**
- Clear, visual interaction display for training purposes
- Integration with RequestBin.net's educational resources
- Professional UI suitable for client demonstrations

---

## 🌐 RequestBin.net Integration

**Experience the full power of modern OOB testing:**

### **🚀 Why RequestBin.net?**
- **Global Infrastructure**: Servers worldwide for reliable testing
- **Advanced Analytics**: Request patterns, geolocation, and timing analysis
- **Team Collaboration**: Share bins and results with your security team
- **Professional Features**: Custom domains, webhooks, and API access

### **🔗 Seamless Experience**
- **One-Click Access**: Direct links from the extension to your RequestBin.net dashboard
- **Synchronized Data**: Automatic synchronization between extension and web platform
- **Enhanced Reporting**: Generate professional reports with detailed interaction analysis

**[Get started with RequestBin.net →](https://requestbin.net/?utm_source=burp_extension&utm_medium=readme)**

---

## 📖 Documentation

- **[Development Guide](DEVELOPMENT.md)** - Complete development documentation
- **[GitHub Issues](https://github.com/requestbin/requestbin-collaborator/issues)** - Report bugs and request features
- **[RequestBin.net Blog](https://blog.requestbin.net/)** - Learn about advanced features

---

## 🤝 Contributing

We welcome contributions from the security community! Whether you're:
- 🐛 Reporting bugs
- 💡 Suggesting new features  
- 🔧 Submitting code improvements
- 📚 Improving documentation

Please see our [Development Guide](DEVELOPMENT.md) for technical details.

---

## 📜 License & Acknowledgments

**MIT License** - See [LICENSE](LICENSE) for details.

### **Acknowledgments**
- Original [interactsh-collaborator](https://github.com/wdahlenburg/interactsh-collaborator) by [@wdahlenburg](https://github.com/wdahlenburg)
- Community contributions from [@TheArqsz](https://github.com/TheArqsz/interactsh-collaborator-rev)
- [Interactsh](https://github.com/projectdiscovery/interactsh) by ProjectDiscovery team

---

## 🌟 Connect with RequestBin.net

- **🌐 Website**: [RequestBin.net](https://requestbin.net)
- **📧 Support**: [Contact RequestBin.net](https://requestbin.net/about)
- **🐦 Updates**: Follow us for the latest security testing insights

---

<div align="center">

**Made with ❤️ by the RequestBin.net team**

*Empowering security professionals worldwide with advanced OOB testing capabilities*

[**Get Started Today →**](https://requestbin.net/?utm_source=burp_extension&utm_medium=readme_cta)

</div>

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
