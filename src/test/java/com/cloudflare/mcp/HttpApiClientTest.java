package com.cloudflare.mcp;

import com.sun.net.httpserver.HttpServer;
import io.swagger.v3.oas.models.Operation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class HttpApiClientTest {

    private HttpServer server;
    private HttpApiClient client;
    private final AtomicReference<Integer> nextStatus = new AtomicReference<>(200);
    private final AtomicReference<String> nextBody = new AtomicReference<>("{\"success\":true}");
    private final AtomicReference<String> lastMethod = new AtomicReference<>();
    private final AtomicReference<String> lastPath = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastMethod.set(exchange.getRequestMethod());
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
        RequestBuilder requestBuilder = new RequestBuilder(
                CloudflareAuth.apiToken("test-token"), 30, baseUrl);
        client = new HttpApiClient(requestBuilder, new RateLimiter(10_000), 10, 50_000);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void returns_response_body_on_success() {
        nextBody.set("{\"success\":true,\"result\":[]}");
        String response = client.execute("/zones", "get", new Operation(), Map.of());
        assertEquals("{\"success\":true,\"result\":[]}", response);
        assertEquals("GET", lastMethod.get());
        assertEquals("/zones", lastPath.get());
    }

    @Test
    void throws_api_exception_with_status_on_http_error() {
        nextStatus.set(403);
        nextBody.set("{\"success\":false}");
        var ex = assertThrows(HttpApiClient.ApiException.class,
                () -> client.execute("/zones", "GET", new Operation(), Map.of()));
        assertTrue(ex.getMessage().contains("HTTP 403"));
    }

    @Test
    void truncates_long_error_bodies() {
        nextStatus.set(500);
        nextBody.set("e".repeat(2000));
        var ex = assertThrows(HttpApiClient.ApiException.class,
                () -> client.execute("/zones", "GET", new Operation(), Map.of()));
        assertTrue(ex.getMessage().contains("[truncated at 500 chars]"));
    }

    @Test
    void wraps_connection_failures() {
        RequestBuilder deadBuilder = new RequestBuilder(
                CloudflareAuth.apiToken("t"), 30, "http://127.0.0.1:1");
        HttpApiClient deadClient = new HttpApiClient(deadBuilder, new RateLimiter(100), 1, 50_000);
        var ex = assertThrows(HttpApiClient.ApiException.class,
                () -> deadClient.execute("/zones", "GET", new Operation(), Map.of()));
        assertTrue(ex.getMessage().startsWith("Request failed"));
    }

    @Test
    void rate_limit_exception_propagates_unwrapped() {
        RateLimiter exhausted = new RateLimiter(0);
        RequestBuilder rb = new RequestBuilder(CloudflareAuth.apiToken("t"), 30);
        HttpApiClient limited = new HttpApiClient(rb, exhausted, 10, 50_000);
        assertThrows(RateLimiter.RateLimitExceededException.class,
                () -> limited.execute("/zones", "GET", new Operation(), Map.of()));
    }

    @Test
    void exposes_max_response_length() {
        assertEquals(50_000, client.maxResponseLength());
    }
}
