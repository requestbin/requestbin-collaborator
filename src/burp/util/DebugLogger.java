package burp.util;

/**
 * Debug utility class to handle conditional logging for development vs production builds
 */
public class DebugLogger {
    
    /**
     * Check if debug mode is enabled through system property or Maven profile
     * Priority: System property > Maven profile > default (false)
     */
    private static final boolean DEBUG_MODE = isDebugEnabled();
    
    private static boolean isDebugEnabled() {
        // Check system property first (runtime override)
        String debugProperty = System.getProperty("interactsh.debug", "").toLowerCase();
        if ("true".equals(debugProperty) || "1".equals(debugProperty)) {
            return true;
        }
        
        // Check if built with debug profile (compile time)
        try {
            String buildMode = DebugLogger.class.getPackage().getImplementationVersion();
            if (buildMode != null && buildMode.contains("DEBUG")) {
                return true;
            }
        } catch (Exception ignored) {
            // Ignore any reflection errors
        }
        
        // Check for debug marker class (injected by Maven profile)
        try {
            Class.forName("burp.util.DebugMarker");
            return true;
        } catch (ClassNotFoundException ignored) {
            // Debug marker not found, production mode
        }
        
        return false; // Default to production mode
    }
    
    /**
     * Log debug message only if debug mode is enabled
     * @param message Debug message to log
     */
    public static void debug(String message) {
        if (DEBUG_MODE && burp.BurpExtender.api != null) {
            burp.BurpExtender.api.logging().logToOutput("[DEBUG] " + message);
        }
    }
    
    /**
     * Log debug message with formatted parameters only if debug mode is enabled
     * @param format Format string
     * @param args Arguments for formatting
     */
    public static void debug(String format, Object... args) {
        if (DEBUG_MODE && burp.BurpExtender.api != null) {
            try {
                String message = String.format(format, args);
                burp.BurpExtender.api.logging().logToOutput("[DEBUG] " + message);
            } catch (Exception e) {
                // Fallback to simple concatenation if formatting fails
                burp.BurpExtender.api.logging().logToOutput("[DEBUG] " + format + " (formatting error)");
            }
        }
    }
    
    /**
     * Log error message (always logged regardless of debug mode)
     * @param message Error message to log
     */
    public static void error(String message) {
        if (burp.BurpExtender.api != null) {
            burp.BurpExtender.api.logging().logToError("[ERROR] " + message);
        }
    }
    
    /**
     * Log info message (always logged regardless of debug mode)
     * @param message Info message to log
     */
    public static void info(String message) {
        if (burp.BurpExtender.api != null) {
            burp.BurpExtender.api.logging().logToOutput("[INFO] " + message);
        }
    }
    
    /**
     * Check if debug mode is currently enabled
     * @return true if debug logging is enabled
     */
    public static boolean isDebugMode() {
        return DEBUG_MODE;
    }
    
    /**
     * Get debug mode status for display
     * @return Human readable debug status
     */
    public static String getDebugStatus() {
        return DEBUG_MODE ? "Development (Debug Enabled)" : "Production (Debug Disabled)";
    }
}