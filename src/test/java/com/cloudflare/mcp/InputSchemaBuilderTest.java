package com.cloudflare.mcp;

import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InputSchemaBuilderTest {

    @Nested
    class PathParameters {

        @Test
        @SuppressWarnings("unchecked")
        void adds_path_params_as_required() {
            Parameter param = new Parameter()
                    .name("zone_id")
                    .in("path")
                    .required(true)
                    .schema(new StringSchema().description("Zone ID"));

            Map<String, Object> result = InputSchemaBuilder.build(List.of(param), null);
            assertEquals("object", result.get("type"));

            Map<String, Object> props = (Map<String, Object>) result.get("properties");
            assertTrue(props.containsKey("zone_id"));

            List<String> required = (List<String>) result.get("required");
            assertTrue(required.contains("zone_id"));
        }
    }

    @Nested
    class QueryParameters {

        @Test
        @SuppressWarnings("unchecked")
        void adds_query_params_as_optional() {
            Parameter param = new Parameter()
                    .name("page")
                    .in("query")
                    .schema(new IntegerSchema());

            Map<String, Object> result = InputSchemaBuilder.build(List.of(param), null);

            Map<String, Object> props = (Map<String, Object>) result.get("properties");
            assertTrue(props.containsKey("page"));

            assertFalse(result.containsKey("required"));
        }

        @Test
        @SuppressWarnings("unchecked")
        void includes_param_description() {
            Parameter param = new Parameter()
                    .name("page")
                    .in("query")
                    .description("Page number")
                    .schema(new IntegerSchema());

            Map<String, Object> result = InputSchemaBuilder.build(List.of(param), null);
            Map<String, Object> props = (Map<String, Object>) result.get("properties");
            Map<String, Object> pageSchema = (Map<String, Object>) props.get("page");
            String desc = (String) pageSchema.get("description");
            assertTrue(desc.contains("Page number"));
            assertTrue(desc.contains("query parameter"));
        }
    }

    @Nested
    class RequestBodyMerging {

        @Test
        @SuppressWarnings("unchecked")
        void merges_object_body_properties() {
            ObjectSchema bodySchema = new ObjectSchema();
            bodySchema.addProperty("name", new StringSchema());
            bodySchema.addProperty("content", new StringSchema());
            bodySchema.setRequired(List.of("name"));

            MediaType mediaType = new MediaType();
            mediaType.setSchema(bodySchema);

            Content content = new Content();
            content.addMediaType("application/json", mediaType);

            RequestBody requestBody = new RequestBody();
            requestBody.setContent(content);

            Map<String, Object> result = InputSchemaBuilder.build(null, requestBody);

            Map<String, Object> props = (Map<String, Object>) result.get("properties");
            assertTrue(props.containsKey("name"));
            assertTrue(props.containsKey("content"));

            List<String> required = (List<String>) result.get("required");
            assertTrue(required.contains("name"));
        }

        @Test
        @SuppressWarnings("unchecked")
        void path_params_not_overwritten_by_body() {
            Parameter pathParam = new Parameter()
                    .name("zone_id")
                    .in("path")
                    .required(true)
                    .schema(new StringSchema().description("Zone ID from path"));

            ObjectSchema bodySchema = new ObjectSchema();
            bodySchema.addProperty("zone_id", new StringSchema().description("Zone ID from body"));

            MediaType mediaType = new MediaType();
            mediaType.setSchema(bodySchema);

            Content content = new Content();
            content.addMediaType("application/json", mediaType);

            RequestBody requestBody = new RequestBody();
            requestBody.setContent(content);

            Map<String, Object> result = InputSchemaBuilder.build(List.of(pathParam), requestBody);
            Map<String, Object> props = (Map<String, Object>) result.get("properties");
            Map<String, Object> zoneIdSchema = (Map<String, Object>) props.get("zone_id");
            String desc = (String) zoneIdSchema.get("description");
            // Path param description includes "(path parameter)" suffix
            assertTrue(desc.contains("path parameter"));
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void no_params_no_body() {
            Map<String, Object> result = InputSchemaBuilder.build(null, null);
            assertEquals("object", result.get("type"));
            assertFalse(result.containsKey("properties"));
            assertFalse(result.containsKey("required"));
        }

        @Test
        void empty_params_list() {
            Map<String, Object> result = InputSchemaBuilder.build(List.of(), null);
            assertEquals("object", result.get("type"));
        }

        @Test
        void param_without_schema_defaults_to_string() {
            Parameter param = new Parameter()
                    .name("filter")
                    .in("query");

            Map<String, Object> result = InputSchemaBuilder.build(List.of(param), null);
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) result.get("properties");
            @SuppressWarnings("unchecked")
            Map<String, Object> filterSchema = (Map<String, Object>) props.get("filter");
            assertEquals("string", filterSchema.get("type"));
        }
    }
}
