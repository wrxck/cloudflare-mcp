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

    private final RequestBuilder builder = new RequestBuilder("test-token", 10, 30);

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
    }
}
