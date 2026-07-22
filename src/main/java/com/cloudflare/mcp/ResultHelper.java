package com.cloudflare.mcp;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

final class ResultHelper {

    private ResultHelper() {}

    static CallToolResult sanitizedResult(String responseBody, int maxLength) {
        String boundary = ContentSanitizer.generateBoundary();
        String wrapped = ContentSanitizer.sanitize(responseBody, boundary, maxLength);
        String securityContext = ContentSanitizer.buildSecurityContext(boundary);
        return CallToolResult.builder()
                .addTextContent(securityContext)
                .addTextContent(wrapped)
                .build();
    }

    static CallToolResult errorResult(String message) {
        return CallToolResult.builder()
                .isError(true)
                .addTextContent(message)
                .build();
    }
}
