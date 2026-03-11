package com.cloudflare.mcp;

import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class InputSchemaBuilder {

    private InputSchemaBuilder() {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    static Map<String, Object> build(List<Parameter> parameters, RequestBody requestBody) {
        var properties = new LinkedHashMap<String, Object>();
        var required = new ArrayList<String>();

        if (parameters != null) {
            for (Parameter param : parameters) {
                String name = param.getName();
                Map<String, Object> paramSchema;

                if (param.getSchema() != null) {
                    paramSchema = new LinkedHashMap<>(SchemaConverter.convert(param.getSchema()));
                } else {
                    paramSchema = new LinkedHashMap<>();
                    paramSchema.put("type", "string");
                }

                String desc = buildParamDescription(param);
                if (desc != null) {
                    paramSchema.put("description", desc);
                }

                properties.put(name, paramSchema);

                if (Boolean.TRUE.equals(param.getRequired())) {
                    required.add(name);
                }
            }
        }

        if (requestBody != null) {
            mergeRequestBody(requestBody, properties, required);
        }

        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        if (!properties.isEmpty()) {
            schema.put("properties", properties);
        }
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void mergeRequestBody(RequestBody requestBody,
                                          Map<String, Object> properties,
                                          List<String> required) {
        Content content = requestBody.getContent();
        if (content == null || content.isEmpty()) return;

        MediaType mediaType = content.get("application/json");
        if (mediaType == null) {
            mediaType = content.values().iterator().next();
        }

        Schema<?> bodySchema = mediaType.getSchema();
        if (bodySchema == null) return;

        Map<String, Object> converted = SchemaConverter.convert(bodySchema);

        if ("object".equals(converted.get("type")) && converted.containsKey("properties")) {
            Map<String, Object> bodyProps = (Map<String, Object>) converted.get("properties");
            bodyProps.forEach((name, propSchema) -> {
                if (!properties.containsKey(name)) {
                    properties.put(name, propSchema);
                }
            });
            if (converted.containsKey("required") && converted.get("required") instanceof List<?> bodyRequired) {
                for (Object r : bodyRequired) {
                    String reqName = (String) r;
                    if (!required.contains(reqName)) {
                        required.add(reqName);
                    }
                }
            }
        } else {
            var bodyProp = new LinkedHashMap<>(converted);
            if (requestBody.getDescription() != null) {
                bodyProp.put("description", requestBody.getDescription());
            }
            properties.put("body", bodyProp);
            if (Boolean.TRUE.equals(requestBody.getRequired())) {
                if (!required.contains("body")) {
                    required.add("body");
                }
            }
        }
    }

    private static String buildParamDescription(Parameter param) {
        StringBuilder desc = new StringBuilder();
        if (param.getDescription() != null) {
            desc.append(param.getDescription());
        }
        String location = param.getIn();
        if (location != null) {
            if (!desc.isEmpty()) desc.append(" ");
            desc.append("(").append(location).append(" parameter)");
        }
        return desc.isEmpty() ? null : desc.toString();
    }
}
