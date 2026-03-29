package com.cloudflare.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record ServerConfig(
        boolean install,
        String claudeBinary,
        String apiToken,
        String apiKey,
        String email,
        List<String> includeTags,
        List<String> excludeTags,
        List<String> includePaths,
        List<String> excludePaths,
        List<String> includeMethods,
        List<String> excludeMethods,
        int maxRequestsPerMinute,
        int maxResponseLength,
        int connectTimeoutSeconds,
        int requestTimeoutSeconds
) {

    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)\\}");

    /**
     * Resolve the authentication strategy from env vars.
     * Prefers Global API Key (CLOUDFLARE_API_KEY + CLOUDFLARE_EMAIL) if set,
     * otherwise falls back to API Token (CLOUDFLARE_API_TOKEN).
     * Returns null if no valid credentials are found.
     */
    CloudflareAuth resolveAuth() {
        if (apiKey != null && !apiKey.isBlank() && email != null && !email.isBlank()) {
            return CloudflareAuth.globalApiKey(apiKey, email);
        }
        if (apiToken != null && !apiToken.isBlank()) {
            return CloudflareAuth.apiToken(apiToken);
        }
        return null;
    }

    static ServerConfig fromArgs(String[] args) {
        boolean install = false;
        String claudeBinary = "claude";
        List<String> includeTags = new ArrayList<>();
        List<String> excludeTags = new ArrayList<>();
        List<String> includePaths = new ArrayList<>();
        List<String> excludePaths = new ArrayList<>();
        List<String> includeMethods = new ArrayList<>();
        List<String> excludeMethods = new ArrayList<>();
        int maxRequestsPerMinute = 240;
        int maxResponseLength = 50_000;
        int connectTimeoutSeconds = 10;
        int requestTimeoutSeconds = 30;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--install" -> install = true;
                case "--claude-binary" -> claudeBinary = nextArg(args, i++);
                case "--include-tags" -> includeTags.addAll(parseCsv(nextArg(args, i++)));
                case "--exclude-tags" -> excludeTags.addAll(parseCsv(nextArg(args, i++)));
                case "--include-paths" -> includePaths.addAll(parseCsv(nextArg(args, i++)));
                case "--exclude-paths" -> excludePaths.addAll(parseCsv(nextArg(args, i++)));
                case "--include-methods" -> includeMethods.addAll(parseCsv(nextArg(args, i++)));
                case "--exclude-methods" -> excludeMethods.addAll(parseCsv(nextArg(args, i++)));
                case "--max-requests-per-minute" -> maxRequestsPerMinute = Integer.parseInt(nextArg(args, i++));
                case "--max-response-length" -> maxResponseLength = Integer.parseInt(nextArg(args, i++));
                case "--connect-timeout" -> connectTimeoutSeconds = Integer.parseInt(nextArg(args, i++));
                case "--request-timeout" -> requestTimeoutSeconds = Integer.parseInt(nextArg(args, i++));
            }
        }

        String apiToken = resolveEnvVars("${CLOUDFLARE_API_TOKEN}");
        String apiKey = resolveEnvVars("${CLOUDFLARE_API_KEY}");
        String email = resolveEnvVars("${CLOUDFLARE_EMAIL}");

        return new ServerConfig(
                install, claudeBinary, apiToken, apiKey, email,
                List.copyOf(includeTags), List.copyOf(excludeTags),
                List.copyOf(includePaths), List.copyOf(excludePaths),
                List.copyOf(includeMethods), List.copyOf(excludeMethods),
                maxRequestsPerMinute, maxResponseLength,
                connectTimeoutSeconds, requestTimeoutSeconds
        );
    }

    static String resolveEnvVars(String input) {
        if (input == null) return null;
        Matcher matcher = ENV_VAR_PATTERN.matcher(input);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String varName = matcher.group(1);
            String value = System.getenv(varName);
            if (value == null) return null;
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String nextArg(String[] args, int i) {
        if (i + 1 >= args.length) {
            throw new IllegalArgumentException("Missing value for " + args[i]);
        }
        return args[i + 1];
    }

    private static List<String> parseCsv(String value) {
        if (value == null || value.isBlank()) return List.of();
        var result = new ArrayList<String>();
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
