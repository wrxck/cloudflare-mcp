package com.cloudflare.mcp;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;

import java.util.List;
import java.util.regex.Pattern;

final class OperationFilter {

    private final List<Pattern> includePaths;
    private final List<Pattern> excludePaths;
    private final List<String> includeMethods;
    private final List<String> excludeMethods;
    private final List<String> includeTags;
    private final List<String> excludeTags;

    OperationFilter(ServerConfig config) {
        this.includePaths = config.includePaths().stream().map(OperationFilter::globToPattern).toList();
        this.excludePaths = config.excludePaths().stream().map(OperationFilter::globToPattern).toList();
        this.includeMethods = config.includeMethods().stream().map(String::toUpperCase).toList();
        this.excludeMethods = config.excludeMethods().stream().map(String::toUpperCase).toList();
        this.includeTags = config.includeTags();
        this.excludeTags = config.excludeTags();
    }

    boolean accepts(String path, PathItem.HttpMethod method, Operation operation) {
        if (!matchesPath(path)) return false;
        if (!matchesMethod(method.name())) return false;
        if (!matchesTags(operation)) return false;
        return true;
    }

    private boolean matchesPath(String path) {
        if (!excludePaths.isEmpty()) {
            for (Pattern p : excludePaths) {
                if (p.matcher(path).matches()) return false;
            }
        }
        if (!includePaths.isEmpty()) {
            for (Pattern p : includePaths) {
                if (p.matcher(path).matches()) return true;
            }
            return false;
        }
        return true;
    }

    private boolean matchesMethod(String method) {
        if (!excludeMethods.isEmpty() && excludeMethods.contains(method)) return false;
        if (!includeMethods.isEmpty()) return includeMethods.contains(method);
        return true;
    }

    private boolean matchesTags(Operation operation) {
        List<String> opTags = operation.getTags();
        if (!excludeTags.isEmpty() && opTags != null) {
            for (String tag : opTags) {
                if (excludeTags.stream().anyMatch(t -> t.equalsIgnoreCase(tag))) return false;
            }
        }
        if (!includeTags.isEmpty()) {
            if (opTags == null || opTags.isEmpty()) return false;
            for (String tag : opTags) {
                if (includeTags.stream().anyMatch(t -> t.equalsIgnoreCase(tag))) return true;
            }
            return false;
        }
        return true;
    }

    static Pattern globToPattern(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> {
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                        regex.append(".*");
                        i++;
                    } else {
                        regex.append("[^/]*");
                    }
                }
                case '?' -> regex.append("[^/]");
                case '.' -> regex.append("\\.");
                case '{' -> regex.append("(");
                case '}' -> regex.append(")");
                case ',' -> regex.append("|");
                default -> regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        regex.append("$");
        return Pattern.compile(regex.toString());
    }
}
