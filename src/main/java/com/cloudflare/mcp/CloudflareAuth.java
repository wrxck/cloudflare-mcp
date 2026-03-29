package com.cloudflare.mcp;

import java.net.http.HttpRequest;

/**
 * Authentication strategy for the Cloudflare API.
 * Supports API Token (Bearer) and Global API Key (X-Auth-Key + X-Auth-Email).
 */
public sealed interface CloudflareAuth {

    /** Apply authentication headers to the request builder. */
    void applyHeaders(HttpRequest.Builder builder);

    /** Create auth using a scoped API Token (Bearer authentication). */
    static CloudflareAuth apiToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("API token must not be null or blank");
        }
        return new ApiToken(token);
    }

    /** Create auth using the Global API Key + account email. */
    static CloudflareAuth globalApiKey(String apiKey, String email) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API key must not be null or blank");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email must not be null or blank");
        }
        return new GlobalApiKey(apiKey, email);
    }

    record ApiToken(String token) implements CloudflareAuth {
        @Override
        public void applyHeaders(HttpRequest.Builder builder) {
            builder.header("Authorization", "Bearer " + token);
        }
    }

    record GlobalApiKey(String apiKey, String email) implements CloudflareAuth {
        @Override
        public void applyHeaders(HttpRequest.Builder builder) {
            builder.header("X-Auth-Key", apiKey);
            builder.header("X-Auth-Email", email);
        }
    }
}
