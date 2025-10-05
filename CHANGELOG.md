# Changelog

All notable changes to RequestBin Collaborator will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2025-10-05

### 🚀 Added
- **Multi-Bin Support**: Create and manage multiple RequestBins with individual tabs
- **RequestBin.net Integration**: Seamless integration with RequestBin.net cloud services
- **Server Management**: Support for RequestBin.net, OAST Pro, and custom Interactsh servers
- **Professional UI/UX**: Modern tab-based interface with empty state guidance
- **Real-Time Polling**: Automatic interaction polling with manual refresh capability
- **Persistent Storage**: Cross-session data persistence with local file storage
- **Protocol Filtering**: Advanced filtering for HTTP, DNS, SMTP, LDAP, SMB, FTP protocols
- **Unread Indicators**: Visual unread counters and interaction status management
- **Toast Notifications**: Non-intrusive status updates and success/error messaging
- **Debug System**: Conditional compilation debug logging with build-time configuration

### 🔒 Security
- **Enhanced Encryption**: AES/RSA encryption for all server communications
- **Secure Registration**: Cryptographic correlation management with automatic key rotation
- **Clean Deregistration**: Proper server cleanup with HTTP deregistration calls
- **Token Management**: Secure server authentication with encrypted token storage

### 🎨 User Experience
- **Welcome Screen**: Professional onboarding for first-time users
- **Empty State Guides**: Contextual help and RequestBin.net feature promotion
- **Responsive Design**: Optimized layouts for different interaction volumes
- **Professional Branding**: RequestBin.net integration with strategic promotional content

### 🛠️ Developer Experience
- **Build Profiles**: Development vs Production builds with conditional debugging
- **Comprehensive Logging**: Detailed tracing for troubleshooting and development
- **Performance Optimization**: Efficient polling, storage, and UI update mechanisms
- **Docker Support**: Containerized build process for consistent deployment

### 🌐 RequestBin.net Features
- **Cloud Infrastructure**: Leverage RequestBin.net's global server network
- **Enhanced Analytics**: Direct access to RequestBin.net's advanced request analysis
- **Team Collaboration**: Integration with RequestBin.net's sharing and collaboration features
- **Professional Reporting**: Seamless workflow integration for security consulting

### 📚 Documentation
- **Professional README**: Comprehensive feature overview with RequestBin.net integration
- **Development Guide**: Complete technical documentation and architecture overview
- **Build Instructions**: Clear development and production build procedures
- **API Documentation**: RequestBin.net integration and custom server support

### 🔄 Architecture
- **Service-Oriented Design**: Modular architecture with BinManager, PollingService, RegistrationService
- **Event-Driven Updates**: Efficient UI updates with property change listeners
- **Thread-Safe Operations**: Proper concurrency handling for real-time features
- **Resource Management**: Automatic cleanup and memory management

---

## [1.0.0] - Base Implementation

### Added
- Initial Burp Suite extension framework
- Basic Interactsh server integration
- HTTP request/response viewing
- Simple interaction logging

---

## Contributors

- **RequestBin.net Team** - Complete rewrite and RequestBin.net integration
- **[@wdahlenburg](https://github.com/wdahlenburg)** - Original interactsh-collaborator project
- **[@TheArqsz](https://github.com/TheArqsz)** - Community contributions and improvements

---

## Links

- **Website**: [RequestBin.net](https://requestbin.net)
- **Documentation**: [Development Guide](DEVELOPMENT.md)
- **Issues**: [GitHub Issues](https://github.com/requestbin/requestbin-collaborator/issues)
- **Releases**: [GitHub Releases](https://github.com/requestbin/requestbin-collaborator/releases)