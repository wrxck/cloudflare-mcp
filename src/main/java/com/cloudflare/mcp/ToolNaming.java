package com.cloudflare.mcp;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class ToolNaming {

    private static final Pattern INVALID_CHARS = Pattern.compile("[^a-zA-Z0-9_-]");
    private static final int MAX_NAME_LENGTH = 64;

    /** Highest numeric suffix handed out per base name, to avoid rescanning from 2. */
    private final Map<String, Integer> suffixCounters = new HashMap<>();
    /** Every name returned so far; suffixed names must not collide with these either. */
    private final Set<String> assignedNames = new HashSet<>();

    String derive(String operationId, String method, String path) {
        String baseName;
        if (operationId != null && !operationId.isBlank()) {
            baseName = sanitize(operationId);
        } else {
            baseName = fromMethodAndPath(method, path);
        }

        if (baseName.length() > MAX_NAME_LENGTH) {
            baseName = baseName.substring(0, MAX_NAME_LENGTH);
        }

        return ensureUnique(baseName);
    }

    static String sanitize(String operationId) {
        String sanitized = INVALID_CHARS.matcher(operationId).replaceAll("_");
        sanitized = sanitized.replaceAll("_+", "_");
        sanitized = sanitized.replaceAll("^_|_$", "");
        if (sanitized.isEmpty()) {
            sanitized = "unnamed";
        }
        return sanitized;
    }

    static String fromMethodAndPath(String method, String path) {
        String normalized = path.replaceAll("^/|/$", "");
        normalized = normalized.replace("{", "").replace("}", "");
        normalized = normalized.replace("/", "_");
        normalized = normalized.replace("-", "_");
        normalized = INVALID_CHARS.matcher(normalized).replaceAll("_");
        normalized = normalized.replaceAll("_+", "_");
        normalized = normalized.replaceAll("^_|_$", "");
        return method.toLowerCase() + "_" + normalized;
    }

    private String ensureUnique(String name) {
        String candidate = name;
        int suffix = suffixCounters.getOrDefault(name, 1);
        while (!assignedNames.add(candidate)) {
            suffix++;
            candidate = withSuffix(name, suffix);
        }
        suffixCounters.put(name, suffix);
        return candidate;
    }

    private static String withSuffix(String name, int suffix) {
        String suffixed = name + "_" + suffix;
        if (suffixed.length() > MAX_NAME_LENGTH) {
            suffixed = name.substring(0, MAX_NAME_LENGTH - String.valueOf(suffix).length() - 1) + "_" + suffix;
        }
        return suffixed;
    }
}
