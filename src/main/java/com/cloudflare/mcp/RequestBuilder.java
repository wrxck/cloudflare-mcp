package com.cloudflare.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.Parameter;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

final class RequestBuilder {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String BASE_URL = "https://api.cloudflare.com/client/v4";

    private final CloudflareAuth auth;
    private final int requestTimeoutSeconds;
    private final String baseUrl;

    RequestBuilder(CloudflareAuth auth, int requestTimeoutSeconds) {
        this(auth, requestTimeoutSeconds, BASE_URL);
    }

    /** Test-only constructor allowing the API base URL to be overridden. */
    RequestBuilder(CloudflareAuth auth, int requestTimeoutSeconds, String baseUrl) {
        this.auth = auth;
        this.requestTimeoutSeconds = requestTimeoutSeconds;
        this.baseUrl = baseUrl;
    }

    HttpRequest build(String path, String method, Operation operation, Map<String, Object> args) {
        if (args == null) args = Map.of();

        String resolvedPath = substitutePath(path, operation, args);
        String queryString = buildQueryString(operation, args);
        String url = baseUrl + resolvedPath;
        if (!queryString.isEmpty()) {
            url += "?" + queryString;
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(requestTimeoutSeconds));

        auth.applyHeaders(builder);

        String upperMethod = method.toUpperCase();
        switch (upperMethod) {
            case "GET" -> builder.GET();
            case "DELETE" -> builder.DELETE();
            case "HEAD" -> builder.method("HEAD", HttpRequest.BodyPublishers.noBody());
            case "OPTIONS" -> builder.method("OPTIONS", HttpRequest.BodyPublishers.noBody());
            default -> {
                String body = buildRequestBody(operation, args);
                if (body != null) {
                    builder.header("Content-Type", RequestBodies.selectContentType(operation.getRequestBody()));
                    builder.method(upperMethod, HttpRequest.BodyPublishers.ofString(body));
                } else {
                    builder.method(upperMethod, HttpRequest.BodyPublishers.noBody());
                }
            }
        }

        return builder.build();
    }

    private String substitutePath(String path, Operation operation, Map<String, Object> args) {
        String resolved = path;
        if (operation.getParameters() != null) {
            for (Parameter param : operation.getParameters()) {
                if ("path".equals(param.getIn())) {
                    Object value = args.get(param.getName());
                    if (value != null) {
                        resolved = resolved.replace("{" + param.getName() + "}",
                                URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8));
                    }
                }
            }
        }
        return resolved;
    }

    private String buildQueryString(Operation operation, Map<String, Object> args) {
        var queryParams = new LinkedHashMap<String, String>();
        if (operation.getParameters() != null) {
            for (Parameter param : operation.getParameters()) {
                if ("query".equals(param.getIn())) {
                    Object value = args.get(param.getName());
                    if (value != null) {
                        queryParams.put(param.getName(), String.valueOf(value));
                    }
                }
            }
        }
        if (queryParams.isEmpty()) return "";

        var sb = new StringBuilder();
        queryParams.forEach((k, v) -> {
            if (!sb.isEmpty()) sb.append("&");
            sb.append(URLEncoder.encode(k, StandardCharsets.UTF_8))
              .append("=")
              .append(URLEncoder.encode(v, StandardCharsets.UTF_8));
        });
        return sb.toString();
    }

    private String buildRequestBody(Operation operation, Map<String, Object> args) {
        MediaType mediaType = RequestBodies.selectMediaType(operation.getRequestBody());
        if (mediaType == null || mediaType.getSchema() == null) return null;

        // Interpret the schema exactly as InputSchemaBuilder advertised it, so every
        // argument the tool exposes is honored here.
        Map<String, Object> converted = SchemaConverter.convert(mediaType.getSchema());
        try {
            if (RequestBodies.flattensIntoProperties(converted)) {
                @SuppressWarnings("unchecked")
                var properties = (Map<String, Object>) converted.get("properties");
                var bodyMap = new LinkedHashMap<String, Object>();
                for (String name : properties.keySet()) {
                    if (args.containsKey(name)) {
                        bodyMap.put(name, args.get(name));
                    }
                }
                return bodyMap.isEmpty() ? null : OBJECT_MAPPER.writeValueAsString(bodyMap);
            } else {
                Object bodyValue = args.get(RequestBodies.RAW_BODY_ARG);
                if (bodyValue == null) return null;
                return OBJECT_MAPPER.writeValueAsString(bodyValue);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize request body", e);
        }
    }
}
