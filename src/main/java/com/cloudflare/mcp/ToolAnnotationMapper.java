package com.cloudflare.mcp;

import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import io.swagger.v3.oas.models.PathItem;

final class ToolAnnotationMapper {

    private ToolAnnotationMapper() {}

    static ToolAnnotations map(PathItem.HttpMethod method, String description) {
        return switch (method) {
            case GET, HEAD, OPTIONS -> new ToolAnnotations(
                    description, true, false, true, true, null);
            case POST -> new ToolAnnotations(
                    description, false, false, false, true, null);
            case PUT -> new ToolAnnotations(
                    description, false, false, true, true, null);
            case PATCH -> new ToolAnnotations(
                    description, false, false, false, true, null);
            case DELETE -> new ToolAnnotations(
                    description, false, true, true, true, null);
            case TRACE -> new ToolAnnotations(
                    description, true, false, true, true, null);
        };
    }
}
