package burp.util;

/**
 * Marker class that indicates this is a development build with debug enabled.
 * This class is only included in development builds through Maven profiles.
 */
public class DebugMarker {
    // This class exists solely as a marker for debug mode detection
    // Its presence indicates that this is a development build
    public static final String BUILD_TYPE = "DEVELOPMENT";
    public static final boolean DEBUG_ENABLED = true;
}