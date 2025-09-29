# Debug Tracing Guide

## 🐛 Debug Features Added

This version includes comprehensive debug logging with **conditional compilation** support. Debug logging is automatically enabled/disabled based on build mode:

- **Development Build** (`mvn package -P dev`): Debug logging enabled
- **Production Build** (`mvn package -P prod`): Debug logging disabled
- **Runtime Override**: Use `-Dinteractsh.debug=true` to enable debug in production builds

## 📊 Debug Log Categories

### **1. Response Processing (`InteractshClient.poll()`)**
```
[DEBUG] Received response body length: X
[DEBUG] Response body preview: {...}
[DEBUG] Response JSON parsed successfully, keys: aes_key, data, ...
[DEBUG] AES key extracted, length: X
```

### **2. AES Key Decryption (`decryptAesKey()`)**
```
[DEBUG] Decrypting AES key - Encrypted length: X
[DEBUG] AES key Base64 decoded - CipherText length: X
[DEBUG] AES key decryption successful - Key length: X
```

### **3. Data Array Processing**
```
[DEBUG] Received data array with X items
[DEBUG] Processing item 1/X, encrypted length: X
```

### **4. Data Decryption (`decryptData()`)**
```
[DEBUG] Decrypting data - Input length: X, Key length: X
[DEBUG] Base64 decoded - CipherText length: X
[DEBUG] IV length: 16, CipherText length: X
[DEBUG] Decryption successful - Result length: X
```

### **5. Entry Creation (`InteractshEntry`)**
```
[DEBUG] Creating InteractshEntry from event: {"protocol":"http",...}
[DEBUG] JSON parsed successfully, keys: protocol, unique-id, remote-address, timestamp, raw-request, raw-response
[DEBUG] Entry parsed - Protocol: http, UID: abc123, Address: 1.2.3.4, RawReq length: X, RawResp length: Y
```

### **6. Protocol-Specific Processing**
```
[DEBUG] Processing details for protocol: http
[DEBUG] Processing HTTP entry
[DEBUG] Details processed successfully, length: X
```

### **7. Table Integration**
```
[DEBUG] Entry added to table successfully
```

### **8. Error Handling**
```
[DEBUG] Exception in polling: JSONException - Unexpected character...
```

## 🔍 Common Issues & Debug Patterns

### **Issue 1: Empty Data Array**
**Pattern:**
```
[DEBUG] Received data array with 0 items
```
**Possible Causes:**
- No interactions received from server
- Server polling interval too frequent
- Authentication issues

### **Issue 2: Decryption Failures**
**Pattern:**
```
[DEBUG] Exception in polling: BadPaddingException - ...
```
**Possible Causes:**
- Wrong private key
- Corrupted encrypted data
- Base64 encoding issues

### **Issue 3: JSON Parsing Errors**
**Pattern:**
```
[DEBUG] Exception in polling: JSONException - Unexpected character at position X
```
**Possible Causes:**
- Invalid response from server
- Malformed decrypted data
- Character encoding issues

### **Issue 4: Missing Fields**
**Pattern:**
```
[DEBUG] JSON parsed successfully, keys: protocol, unique-id
# Missing: raw-request, raw-response
```
**Possible Causes:**
- Server returning partial data
- Different API version
- Protocol-specific field variations

## 🛠 Debugging Workflow

### **Step 1: Enable Burp Suite Output**
1. Open Burp Suite
2. Go to **Extensions** → **Installed** → **RequestBin Collaborator**
3. Check **Output** tab for debug messages

### **Step 2: Monitor Polling Cycle**
Watch for this sequence in logs:
```
[DEBUG] Received response body length: X
[DEBUG] Response JSON parsed successfully
[DEBUG] Received data array with X items
[DEBUG] Processing item 1/X
[DEBUG] Decryption successful
[DEBUG] Entry added to table successfully
```

### **Step 3: Identify Break Points**
- **No response**: Check network connectivity
- **JSON parse fail**: Check server response format
- **Decryption fail**: Check keys and encryption parameters
- **Entry creation fail**: Check required JSON fields

### **Step 4: Analyze Data Content**
Look for:
- **Data preview**: First 500 characters of decrypted data
- **Field lengths**: Ensure raw-request/raw-response are populated
- **Protocol types**: DNS, HTTP, SMTP, etc.

## 🔧 Advanced Debugging

### **Custom Log Filtering**
Search Burp Output for specific patterns:
- `[DEBUG] Decrypted data preview:` - See actual interaction data
- `[DEBUG] Entry parsed -` - Verify field extraction
- `[DEBUG] Exception in polling:` - Find errors

### **Performance Monitoring**
Track these metrics:
- Response body length (should be > 0)
- Data array length (interactions count)
- Decryption success rate
- Entry processing time

### **Manual Testing**
1. **Trigger interactions**: Use `curl http://[subdomain].interact.sh`
2. **Check polling**: Look for data array > 0
3. **Verify decryption**: Check for valid JSON in preview
4. **Confirm display**: Ensure entries appear in table

## 📝 Debug Log Analysis Examples

### **Successful Interaction**
```
[DEBUG] Received response body length: 1247
[DEBUG] Response JSON parsed successfully, keys: aes_key, data
[DEBUG] AES key extracted, length: 256
[DEBUG] AES key decryption successful - Key length: 32
[DEBUG] Received data array with 1 items
[DEBUG] Processing item 1/1, encrypted length: 892
[DEBUG] Decryption successful - Result length: 654
[DEBUG] Creating InteractshEntry from event: {"protocol":"http","unique-id":"abc123"...}
[DEBUG] Entry parsed - Protocol: http, UID: abc123, Address: 1.2.3.4, RawReq length: 156, RawResp length: 287
[DEBUG] Processing HTTP entry
[DEBUG] Entry added to table successfully
```

### **Empty Response**
```
[DEBUG] Received response body length: 67
[DEBUG] Response body preview: {"aes_key":"...", "data":[]}
[DEBUG] Received data array with 0 items
```

### **Decryption Error**
```
[DEBUG] Decrypting data - Input length: 892, Key length: 32
[DEBUG] Exception in polling: BadPaddingException - Decryption error
```

## 🎯 Performance Optimization

Based on debug logs, you can:
1. **Adjust polling interval** if too many empty responses
2. **Optimize key caching** if decryption is slow
3. **Batch process entries** if high volume
4. **Filter protocols** if only specific types needed

## 🎛 Debug Mode Control

### **Build-time Configuration:**
```bash
# Development build (debug enabled)
mvn clean package -P dev

# Production build (debug disabled)  
mvn clean package -P prod
```

### **Runtime Configuration:**
```bash
# Enable debug in production build
java -Dinteractsh.debug=true -jar burpsuite.jar
```

### **Programmatic Check:**
```java
// Check if debug mode is active
boolean isDebug = DebugLogger.isDebugMode();

// Get current mode status
String status = DebugLogger.getDebugStatus();
```

---

**Note:** Debug logging is automatically managed through build profiles. Production builds exclude debug infrastructure entirely for optimal performance and smaller file size.