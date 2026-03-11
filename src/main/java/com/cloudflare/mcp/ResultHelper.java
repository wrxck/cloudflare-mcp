package com.cloudflare.mcp;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

final class ResultHelper {

    private ResultHelper() {}

    static CallToolResult sanitizedResult(String responseBody, int maxLength) {
        String boundary = ContentSanitizer.generateBoundary();
        String truncated = ContentSanitizer.truncate(responseBody, maxLength);
        String wrapped = ContentSanitizer.sanitize(truncated, boundary, maxLength);
        String securityContext = ContentSanitizer.buildSecurityContext(boundary);
        return CallToolResult.builder()
                .addTextContent(securityContext)
                .addTextContent(wrapped)
                .build();
    }

    static CallToolResult textResult(String text) {
        return CallToolResult.builder()
                .addTextContent(text)
                .build();
    }

    static CallToolResult errorResult(String message) {
        return CallToolResult.builder()
                .isError(true)
                .addTextContent(message)
                .build();
    }
}
