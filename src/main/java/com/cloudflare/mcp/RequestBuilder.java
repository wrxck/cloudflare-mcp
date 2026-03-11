package com.cloudflare.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;

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

    private final String apiToken;
    private final int connectTimeoutSeconds;
    private final int requestTimeoutSeconds;

    RequestBuilder(String apiToken, int connectTimeoutSeconds, int requestTimeoutSeconds) {
        this.apiToken = apiToken;
        this.connectTimeoutSeconds = connectTimeoutSeconds;
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }

    HttpRequest build(String path, String method, Operation operation, Map<String, Object> args) {
        if (args == null) args = Map.of();

        String resolvedPath = substitutePath(path, operation, args);
        String queryString = buildQueryString(operation, args);
        String url = BASE_URL + resolvedPath;
        if (!queryString.isEmpty()) {
            url += "?" + queryString;
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                .header("Authorization", "Bearer " + apiToken)
                .header("Content-Type", "application/json");

        String upperMethod = method.toUpperCase();
        switch (upperMethod) {
            case "GET" -> builder.GET();
            case "DELETE" -> builder.DELETE();
            case "HEAD" -> builder.method("HEAD", HttpRequest.BodyPublishers.noBody());
            case "OPTIONS" -> builder.method("OPTIONS", HttpRequest.BodyPublishers.noBody());
            default -> {
                String body = buildRequestBody(operation, args);
                if (body != null) {
                    builder.header("Content-Type", getContentType(operation));
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private String buildRequestBody(Operation operation, Map<String, Object> args) {
        RequestBody requestBody = operation.getRequestBody();
        if (requestBody == null) return null;

        Content content = requestBody.getContent();
        if (content == null || content.isEmpty()) return null;

        MediaType mediaType = content.get("application/json");
        if (mediaType == null) {
            mediaType = content.values().iterator().next();
        }

        var bodySchema = mediaType.getSchema();
        if (bodySchema == null) return null;

        try {
            if ("object".equals(bodySchema.getType()) || bodySchema.getProperties() != null) {
                var bodyMap = new LinkedHashMap<String, Object>();
                if (bodySchema.getProperties() != null) {
                    for (Object key : bodySchema.getProperties().keySet()) {
                        String name = (String) key;
                        if (args.containsKey(name)) {
                            bodyMap.put(name, args.get(name));
                        }
                    }
                }
                return bodyMap.isEmpty() ? null : OBJECT_MAPPER.writeValueAsString(bodyMap);
            } else {
                Object bodyValue = args.get("body");
                if (bodyValue == null) return null;
                return OBJECT_MAPPER.writeValueAsString(bodyValue);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize request body", e);
        }
    }

    private String getContentType(Operation operation) {
        if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
            if (operation.getRequestBody().getContent().containsKey("application/json")) {
                return "application/json";
            }
            return operation.getRequestBody().getContent().keySet().iterator().next();
        }
        return "application/json";
    }
}
