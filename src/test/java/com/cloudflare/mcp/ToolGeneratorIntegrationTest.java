package com.cloudflare.mcp;

import com.sun.net.httpserver.HttpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end: OpenAPI model -> generated tools -> handler invocation over HTTP.
 */
class ToolGeneratorIntegrationTest {

    private HttpServer server;
    private ToolGenerator generator;
    private final AtomicReference<Integer> nextStatus = new AtomicReference<>(200);
    private final AtomicReference<String> nextBody = new AtomicReference<>("{\"success\":true}");
    private final AtomicReference<String> lastPath = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastPath.set(exchange.getRequestURI().toString());
            exchange.getRequestBody().readAllBytes();
            byte[] out = nextBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(nextStatus.get(), out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        ServerConfig config = ServerConfig.fromArgs(new String[]{});
        RequestBuilder requestBuilder = new RequestBuilder(
                CloudflareAuth.apiToken("test-token"), 30, baseUrl);
        HttpApiClient apiClient = new HttpApiClient(requestBuilder, new RateLimiter(10_000), 10, 50_000);
        generator = new ToolGenerator(new OperationFilter(config), apiClient);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private static OpenAPI openApiWith(String path, PathItem pathItem) {
        OpenAPI openAPI = new OpenAPI();
        Paths paths = new Paths();
        paths.addPathItem(path, pathItem);
        openAPI.setPaths(paths);
        return openAPI;
    }

    @Test
    void generates_tool_with_name_description_and_schema() {
        Parameter param = new Parameter().name("zone_id").in("path").required(true)
                .schema(new StringSchema());
        Operation op = new Operation().operationId("zone-details").summary("Zone details");
        op.setParameters(List.of(param));
        PathItem item = new PathItem();
        item.setGet(op);

        List<SyncToolSpecification> tools = generator.generate(
                openApiWith("/zones/{zone_id}", item));

        assertEquals(1, tools.size());
        McpSchema.Tool tool = tools.get(0).tool();
        assertEquals("zone-details", tool.name());
        assertEquals("Zone details", tool.description());
        assertTrue(tool.inputSchema().properties().containsKey("zone_id"));
        assertEquals(List.of("zone_id"), tool.inputSchema().required());
        assertTrue(tool.annotations().readOnlyHint());
    }

    @Test
    void handler_executes_request_and_wraps_response() {
        PathItem item = new PathItem();
        item.setGet(new Operation().operationId("list-zones"));
        List<SyncToolSpecification> tools = generator.generate(openApiWith("/zones", item));

        nextBody.set("{\"success\":true,\"result\":[{\"id\":\"z1\"}]}");
        McpSchema.CallToolResult result = tools.get(0).callHandler()
                .apply(null, new McpSchema.CallToolRequest("list-zones", Map.of()));

        assertNotEquals(Boolean.TRUE, result.isError());
        assertEquals(2, result.content().size());
        assertTrue(result.content().get(0).toString().contains("UNTRUSTED"));
        assertTrue(result.content().get(1).toString().contains("z1"));
        assertEquals("/zones", lastPath.get());
    }

    @Test
    void handler_substitutes_path_params_from_arguments() {
        Parameter param = new Parameter().name("zone_id").in("path").required(true)
                .schema(new StringSchema());
        Operation op = new Operation().operationId("zone-details");
        op.setParameters(List.of(param));
        PathItem item = new PathItem();
        item.setGet(op);
        List<SyncToolSpecification> tools = generator.generate(
                openApiWith("/zones/{zone_id}", item));

        tools.get(0).callHandler().apply(null,
                new McpSchema.CallToolRequest("zone-details", Map.of("zone_id", "abc123")));

        assertEquals("/zones/abc123", lastPath.get());
    }

    @Test
    void handler_null_arguments_treated_as_empty() {
        PathItem item = new PathItem();
        item.setGet(new Operation().operationId("list-zones"));
        List<SyncToolSpecification> tools = generator.generate(openApiWith("/zones", item));

        McpSchema.CallToolResult result = tools.get(0).callHandler()
                .apply(null, new McpSchema.CallToolRequest("list-zones", null));
        assertNotEquals(Boolean.TRUE, result.isError());
    }

    @Test
    void handler_returns_error_result_on_api_failure() {
        PathItem item = new PathItem();
        item.setGet(new Operation().operationId("list-zones"));
        List<SyncToolSpecification> tools = generator.generate(openApiWith("/zones", item));

        nextStatus.set(500);
        nextBody.set("{\"success\":false}");
        McpSchema.CallToolResult result = tools.get(0).callHandler()
                .apply(null, new McpSchema.CallToolRequest("list-zones", Map.of()));

        assertEquals(Boolean.TRUE, result.isError());
        assertTrue(result.content().get(0).toString().contains("HTTP 500"));
    }

    @Test
    void filtered_operations_are_skipped() {
        ServerConfig config = ServerConfig.fromArgs(new String[]{"--include-methods", "GET"});
        ToolGenerator filtered = new ToolGenerator(
                new OperationFilter(config),
                new HttpApiClient(new RequestBuilder(CloudflareAuth.apiToken("t"), 30),
                        new RateLimiter(100), 10, 50_000));

        PathItem item = new PathItem();
        item.setGet(new Operation().operationId("list-zones"));
        item.setDelete(new Operation().operationId("delete-zone"));

        List<SyncToolSpecification> tools = filtered.generate(openApiWith("/zones", item));
        assertEquals(1, tools.size());
        assertEquals("list-zones", tools.get(0).tool().name());
    }

    @Test
    void operation_without_id_gets_method_path_name() {
        PathItem item = new PathItem();
        item.setPost(new Operation());
        List<SyncToolSpecification> tools = generator.generate(openApiWith("/zones", item));
        assertEquals("post_zones", tools.get(0).tool().name());
        assertEquals("POST /zones", tools.get(0).tool().description());
    }
}
