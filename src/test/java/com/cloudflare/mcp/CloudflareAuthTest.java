package com.cloudflare.mcp;

import org.junit.jupiter.api.Test;
import java.net.http.HttpRequest;
import static org.junit.jupiter.api.Assertions.*;

class CloudflareAuthTest {

    @Test
    void tokenAuthAppliesBearerHeader() {
        var auth = CloudflareAuth.apiToken("my-token-123");
        var builder = HttpRequest.newBuilder().uri(java.net.URI.create("https://example.com"));
        auth.applyHeaders(builder);
        var request = builder.GET().build();
        assertEquals("Bearer my-token-123",
                request.headers().firstValue("Authorization").orElse(null));
    }

    @Test
    void globalKeyAuthAppliesKeyAndEmailHeaders() {
        var auth = CloudflareAuth.globalApiKey("my-key-456", "user@example.com");
        var builder = HttpRequest.newBuilder().uri(java.net.URI.create("https://example.com"));
        auth.applyHeaders(builder);
        var request = builder.GET().build();
        assertEquals("my-key-456",
                request.headers().firstValue("X-Auth-Key").orElse(null));
        assertEquals("user@example.com",
                request.headers().firstValue("X-Auth-Email").orElse(null));
    }

    @Test
    void tokenAuthRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> CloudflareAuth.apiToken(null));
    }

    @Test
    void tokenAuthRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> CloudflareAuth.apiToken("  "));
    }

    @Test
    void globalKeyAuthRejectsNullKey() {
        assertThrows(IllegalArgumentException.class,
                () -> CloudflareAuth.globalApiKey(null, "user@example.com"));
    }

    @Test
    void globalKeyAuthRejectsNullEmail() {
        assertThrows(IllegalArgumentException.class,
                () -> CloudflareAuth.globalApiKey("key", null));
    }
}
