package burp.util;

import java.io.File;
import java.util.Locale;

/**
 * Utilities for safely constructing local storage file paths.
 */
public final class StorageFileUtils {
    private static final int MAX_IDENTIFIER_LENGTH = 128;
    private static final String DEFAULT_IDENTIFIER = "unknown";

    private StorageFileUtils() {
        // Utility class
    }

    /**
     * Sanitizes an identifier so it is safe to use as part of a filename.
     */
    public static String sanitizeIdentifierForFilename(String identifier) {
        if (identifier == null) {
            return DEFAULT_IDENTIFIER;
        }

        String sanitized = identifier
            .trim()
            .replace('\\', '_')
            .replace('/', '_')
            .replaceAll("[^a-zA-Z0-9._-]", "_");

        // Avoid hidden-file style names and parent-directory patterns.
        while (sanitized.startsWith(".")) {
            sanitized = sanitized.substring(1);
        }
        sanitized = sanitized.replace("..", "_");

        if (sanitized.isEmpty()) {
            sanitized = DEFAULT_IDENTIFIER;
        }

        if (sanitized.length() > MAX_IDENTIFIER_LENGTH) {
            sanitized = sanitized.substring(0, MAX_IDENTIFIER_LENGTH);
        }

        return sanitized.toLowerCase(Locale.ROOT);
    }

    /**
     * Builds the storage file path for a bin/interaction identifier.
     */
    public static File interactionsFile(File storageDir, String identifier) {
        String safeIdentifier = sanitizeIdentifierForFilename(identifier);
        return new File(storageDir, "interactions-" + safeIdentifier + ".json");
    }
}
