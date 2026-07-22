package com.cloudflare.mcp;

import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.RequestBody;

import java.util.Map;

/**
 * Shared interpretation of OpenAPI request bodies. InputSchemaBuilder (which
 * advertises the tool's input schema) and RequestBuilder (which serializes the
 * actual HTTP body from tool args) must agree on which media type is used and
 * whether an object body is flattened into top-level args or passed as a raw
 * "body" argument — otherwise tools advertise arguments that are silently ignored.
 */
final class RequestBodies {

    static final String JSON_CONTENT_TYPE = "application/json";

    /** Name of the argument used when a body schema is not a flattenable object. */
    static final String RAW_BODY_ARG = "body";

    private RequestBodies() {}

    /** Picks the JSON media type if present, otherwise the first declared one. */
    static MediaType selectMediaType(RequestBody requestBody) {
        if (requestBody == null) return null;
        Content content = requestBody.getContent();
        if (content == null || content.isEmpty()) return null;
        MediaType json = content.get(JSON_CONTENT_TYPE);
        return json != null ? json : content.values().iterator().next();
    }

    /** Content type matching {@link #selectMediaType}'s choice. */
    static String selectContentType(RequestBody requestBody) {
        if (requestBody == null || requestBody.getContent() == null
                || requestBody.getContent().isEmpty()
                || requestBody.getContent().containsKey(JSON_CONTENT_TYPE)) {
            return JSON_CONTENT_TYPE;
        }
        return requestBody.getContent().keySet().iterator().next();
    }

    /**
     * True when a converted body schema (see SchemaConverter) is an object whose
     * properties are flattened into top-level tool arguments; false when the body
     * is instead exposed as a single raw {@link #RAW_BODY_ARG} argument.
     */
    static boolean flattensIntoProperties(Map<String, Object> convertedSchema) {
        return "object".equals(convertedSchema.get("type"))
                && convertedSchema.containsKey("properties");
    }
}
