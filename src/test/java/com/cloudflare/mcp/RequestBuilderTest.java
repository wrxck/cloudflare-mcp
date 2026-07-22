package com.cloudflare.mcp;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RequestBuilderTest {

    private final RequestBuilder builder = new RequestBuilder(
            CloudflareAuth.apiToken("test-token"), 30);

    @Nested
    class BasicRequests {

        @Test
        void get_request_url() {
            Operation op = new Operation();
            HttpRequest request = builder.build("/zones", "GET", op, Map.of());

            assertEquals("https://api.cloudflare.com/client/v4/zones", request.uri().toString());
            assertEquals("GET", request.method());
        }

        @Test
        void includes_bearer_auth() {
            Operation op = new Operation();
            HttpRequest request = builder.build("/zones", "GET", op, Map.of());

            var authHeader = request.headers().firstValue("Authorization");
            assertTrue(authHeader.isPresent());
            assertEquals("Bearer test-token", authHeader.get());
        }

        @Test
        void delete_request() {
            Operation op = new Operation();
            HttpRequest request = builder.build("/zones/123", "DELETE", op, Map.of());

            assertEquals("DELETE", request.method());
        }

        @Test
        void head_and_options_have_no_body() {
            for (String method : List.of("HEAD", "OPTIONS")) {
                HttpRequest request = builder.build("/zones", method, new Operation(), Map.of());
                assertEquals(method, request.method());
                String body = extractBody(request);
                assertTrue(body == null || body.isEmpty());
            }
        }

        @Test
        void null_args_treated_as_empty() {
            HttpRequest request = builder.build("/zones", "GET", new Operation(), null);
            assertEquals("https://api.cloudflare.com/client/v4/zones", request.uri().toString());
        }

        @Test
        void post_without_request_body_definition_sends_no_body() {
            HttpRequest request = builder.build("/zones", "POST", new Operation(), Map.of());
            assertEquals("POST", request.method());
            assertEquals("", extractBody(request));
            assertTrue(request.headers().firstValue("Content-Type").isEmpty());
        }
    }

    @Nested
    class PathSubstitution {

        @Test
        void substitutes_path_params() {
            Parameter param = new Parameter()
                    .name("zone_id")
                    .in("path")
                    .schema(new StringSchema());

            Operation op = new Operation();
            op.setParameters(List.of(param));

            HttpRequest request = builder.build("/zones/{zone_id}", "GET", op,
                    Map.of("zone_id", "abc123"));

            assertTrue(request.uri().toString().contains("/zones/abc123"));
        }

        @Test
        void missing_path_param_fails_fast() {
            Parameter param = new Parameter()
                    .name("zone_id")
                    .in("path")
                    .schema(new StringSchema());

            Operation op = new Operation();
            op.setParameters(List.of(param));

            // The placeholder stays unsubstituted, producing an invalid URI — the tool
            // call fails instead of silently hitting a wrong endpoint.
            assertThrows(IllegalArgumentException.class,
                    () -> builder.build("/zones/{zone_id}", "GET", op, Map.of()));
        }

        @Test
        void url_encodes_path_params() {
            Parameter param = new Parameter()
                    .name("name")
                    .in("path")
                    .schema(new StringSchema());

            Operation op = new Operation();
            op.setParameters(List.of(param));

            HttpRequest request = builder.build("/items/{name}", "GET", op,
                    Map.of("name", "hello world"));

            assertTrue(request.uri().toString().contains("hello+world") ||
                    request.uri().toString().contains("hello%20world"));
        }
    }

    @Nested
    class QueryParameters {

        @Test
        void adds_query_params() {
            Parameter param = new Parameter()
                    .name("page")
                    .in("query")
                    .schema(new IntegerSchema());

            Operation op = new Operation();
            op.setParameters(List.of(param));

            HttpRequest request = builder.build("/zones", "GET", op,
                    Map.of("page", 2));

            assertTrue(request.uri().toString().contains("page=2"));
        }

        @Test
        void multiple_query_params() {
            Parameter p1 = new Parameter().name("page").in("query").schema(new IntegerSchema());
            Parameter p2 = new Parameter().name("per_page").in("query").schema(new IntegerSchema());

            Operation op = new Operation();
            op.setParameters(List.of(p1, p2));

            HttpRequest request = builder.build("/zones", "GET", op,
                    Map.of("page", 1, "per_page", 20));

            String uri = request.uri().toString();
            assertTrue(uri.contains("page=1"));
            assertTrue(uri.contains("per_page=20"));
        }

        @Test
        void skips_null_query_params() {
            Parameter param = new Parameter()
                    .name("page")
                    .in("query")
                    .schema(new IntegerSchema());

            Operation op = new Operation();
            op.setParameters(List.of(param));

            HttpRequest request = builder.build("/zones", "GET", op, Map.of());

            assertFalse(request.uri().toString().contains("page="));
        }
    }

    private static String extractBody(HttpRequest request) {
        if (request.bodyPublisher().isEmpty()) return null;
        var subscriber = java.net.http.HttpResponse.BodySubscribers
                .ofString(java.nio.charset.StandardCharsets.UTF_8);
        request.bodyPublisher().get().subscribe(new java.util.concurrent.Flow.Subscriber<>() {
            @Override public void onSubscribe(java.util.concurrent.Flow.Subscription s) {
                subscriber.onSubscribe(s);
            }
            @Override public void onNext(java.nio.ByteBuffer item) {
                subscriber.onNext(List.of(item));
            }
            @Override public void onError(Throwable t) { subscriber.onError(t); }
            @Override public void onComplete() { subscriber.onComplete(); }
        });
        return subscriber.getBody().toCompletableFuture().join();
    }

    private static Operation operationWithBodySchema(Schema<?> bodySchema) {
        MediaType mediaType = new MediaType();
        mediaType.setSchema(bodySchema);
        Content content = new Content();
        content.addMediaType("application/json", mediaType);
        RequestBody requestBody = new RequestBody();
        requestBody.setContent(content);
        Operation op = new Operation();
        op.setRequestBody(requestBody);
        return op;
    }

    @Nested
    class RequestBodyBuilding {

        @Test
        void post_with_json_body() {
            ObjectSchema bodySchema = new ObjectSchema();
            bodySchema.addProperty("name", new StringSchema());

            MediaType mediaType = new MediaType();
            mediaType.setSchema(bodySchema);

            Content content = new Content();
            content.addMediaType("application/json", mediaType);

            RequestBody requestBody = new RequestBody();
            requestBody.setContent(content);

            Operation op = new Operation();
            op.setRequestBody(requestBody);

            HttpRequest request = builder.build("/zones", "POST", op,
                    Map.of("name", "example.com"));

            assertEquals("POST", request.method());
            assertTrue(request.headers().firstValue("Content-Type")
                    .orElse("").contains("application/json"));
        }

        // The request body must honor the same schema interpretation that
        // InputSchemaBuilder advertises to the model. For an allOf composed object
        // schema, InputSchemaBuilder flattens properties into top-level args, so
        // RequestBuilder must pick those args up too.
        @Test
        void allOf_body_schema_accepts_flattened_args_like_advertised_schema() {
            ObjectSchema part = new ObjectSchema();
            part.addProperty("name", new StringSchema());
            ComposedSchema composed = new ComposedSchema();
            composed.setAllOf(List.of(part));
            Operation op = operationWithBodySchema(composed);

            // sanity: the advertised input schema exposes "name" at top level
            Map<String, Object> advertised = InputSchemaBuilder.build(null, op.getRequestBody());
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) advertised.get("properties");
            assertTrue(props.containsKey("name"), "precondition: schema advertises flattened 'name'");

            HttpRequest request = builder.build("/zones", "POST", op, Map.of("name", "example.com"));
            assertEquals("{\"name\":\"example.com\"}", extractBody(request));
        }

        @Test
        void unserializable_body_value_fails_with_clear_error() {
            ObjectSchema bare = new ObjectSchema();
            Operation op = operationWithBodySchema(bare);

            var ex = assertThrows(RuntimeException.class, () ->
                    builder.build("/zones", "POST", op, Map.of("body", new Object())));
            assertTrue(ex.getMessage().contains("Failed to serialize request body"));
        }

        @Test
        void non_json_media_type_sets_matching_content_type() {
            MediaType mediaType = new MediaType();
            mediaType.setSchema(new StringSchema());
            Content content = new Content();
            content.addMediaType("text/plain", mediaType);
            RequestBody requestBody = new RequestBody();
            requestBody.setContent(content);
            Operation op = new Operation();
            op.setRequestBody(requestBody);

            HttpRequest request = builder.build("/upload", "PUT", op, Map.of("body", "hello"));
            assertEquals("text/plain", request.headers().firstValue("Content-Type").orElse(null));
            assertEquals("\"hello\"", extractBody(request));
        }

        @Test
        void body_omitted_when_no_matching_args() {
            ObjectSchema bodySchema = new ObjectSchema();
            bodySchema.addProperty("name", new StringSchema());
            Operation op = operationWithBodySchema(bodySchema);

            HttpRequest request = builder.build("/zones", "POST", op, Map.of("unrelated", "x"));
            String body = extractBody(request);
            assertTrue(body == null || body.isEmpty());
        }

        // An object schema with no properties is advertised by InputSchemaBuilder as a
        // raw "body" argument; RequestBuilder must serialize that argument.
        @Test
        void object_schema_without_properties_uses_raw_body_arg() {
            ObjectSchema bare = new ObjectSchema(); // type=object, no properties
            Operation op = operationWithBodySchema(bare);

            Map<String, Object> advertised = InputSchemaBuilder.build(null, op.getRequestBody());
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) advertised.get("properties");
            assertTrue(props.containsKey("body"), "precondition: schema advertises raw 'body' arg");

            HttpRequest request = builder.build("/zones", "POST", op,
                    Map.of("body", Map.of("anything", "goes")));
            assertEquals("{\"anything\":\"goes\"}", extractBody(request));
        }
    }
}
