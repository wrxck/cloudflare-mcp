package com.cloudflare.mcp;

import io.swagger.v3.oas.models.media.Schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SchemaConverter {

    private SchemaConverter() {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    static Map<String, Object> convert(Schema<?> schema) {
        if (schema == null) {
            return Map.of("type", "object");
        }

        var result = new LinkedHashMap<String, Object>();

        if (schema.getAllOf() != null && !schema.getAllOf().isEmpty()) {
            return convertAllOf(schema.getAllOf());
        }
        if (schema.getOneOf() != null && !schema.getOneOf().isEmpty()) {
            return convertOneOf(schema.getOneOf());
        }
        if (schema.getAnyOf() != null && !schema.getAnyOf().isEmpty()) {
            return convertAnyOf(schema.getAnyOf());
        }

        String type = schema.getType();
        if (type == null && schema.getTypes() != null && !schema.getTypes().isEmpty()) {
            type = schema.getTypes().iterator().next();
        }

        if (type != null) {
            result.put("type", type);
        }

        if (schema.getDescription() != null) {
            result.put("description", schema.getDescription());
        }

        if (schema.getFormat() != null) {
            result.put("format", schema.getFormat());
        }

        if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
            result.put("enum", schema.getEnum());
        }

        if (schema.getDefault() != null) {
            result.put("default", schema.getDefault());
        }

        if (schema.getMinimum() != null) {
            result.put("minimum", schema.getMinimum());
        }
        if (schema.getMaximum() != null) {
            result.put("maximum", schema.getMaximum());
        }
        if (schema.getMinLength() != null) {
            result.put("minLength", schema.getMinLength());
        }
        if (schema.getMaxLength() != null) {
            result.put("maxLength", schema.getMaxLength());
        }
        if (schema.getPattern() != null) {
            result.put("pattern", schema.getPattern());
        }

        if ("object".equals(type) || schema.getProperties() != null) {
            if (!result.containsKey("type")) {
                result.put("type", "object");
            }
            if (schema.getProperties() != null) {
                var props = new LinkedHashMap<String, Object>();
                schema.getProperties().forEach((name, propSchema) ->
                        props.put(name, convert((Schema<?>) propSchema)));
                result.put("properties", props);
            }
            if (schema.getRequired() != null && !schema.getRequired().isEmpty()) {
                result.put("required", new ArrayList<>(schema.getRequired()));
            }
        }

        if ("array".equals(type)) {
            if (schema.getItems() != null) {
                result.put("items", convert(schema.getItems()));
            }
            if (schema.getMinItems() != null) {
                result.put("minItems", schema.getMinItems());
            }
            if (schema.getMaxItems() != null) {
                result.put("maxItems", schema.getMaxItems());
            }
        }

        if (result.isEmpty()) {
            result.put("type", "object");
        }

        return result;
    }

    @SuppressWarnings("rawtypes")
    private static Map<String, Object> convertAllOf(List<Schema> schemas) {
        var merged = new LinkedHashMap<String, Object>();
        merged.put("type", "object");
        var properties = new LinkedHashMap<String, Object>();
        var required = new ArrayList<String>();
        String description = null;

        for (Schema<?> s : schemas) {
            Map<String, Object> converted = convert(s);
            if (converted.containsKey("properties") && converted.get("properties") instanceof Map<?,?> props) {
                props.forEach((k, v) -> properties.put((String) k, v));
            }
            if (converted.containsKey("required") && converted.get("required") instanceof List<?> req) {
                req.forEach(r -> {
                    if (!required.contains(r)) required.add((String) r);
                });
            }
            if (converted.containsKey("description") && description == null) {
                description = (String) converted.get("description");
            }
        }

        if (!properties.isEmpty()) {
            merged.put("properties", properties);
        }
        if (!required.isEmpty()) {
            merged.put("required", required);
        }
        if (description != null) {
            merged.put("description", description);
        }
        return merged;
    }

    @SuppressWarnings("rawtypes")
    private static Map<String, Object> convertOneOf(List<Schema> schemas) {
        var result = new LinkedHashMap<String, Object>();
        result.put("oneOf", schemas.stream().map(s -> convert((Schema<?>) s)).toList());
        return result;
    }

    @SuppressWarnings("rawtypes")
    private static Map<String, Object> convertAnyOf(List<Schema> schemas) {
        var result = new LinkedHashMap<String, Object>();
        result.put("anyOf", schemas.stream().map(s -> convert((Schema<?>) s)).toList());
        return result;
    }
}
