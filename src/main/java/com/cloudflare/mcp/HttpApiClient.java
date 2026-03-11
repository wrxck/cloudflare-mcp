package com.cloudflare.mcp;

import io.swagger.v3.oas.models.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

final class HttpApiClient {

    private static final Logger log = LoggerFactory.getLogger(HttpApiClient.class);

    private final HttpClient httpClient;
    private final RequestBuilder requestBuilder;
    private final RateLimiter rateLimiter;
    private final int maxResponseLength;

    HttpApiClient(RequestBuilder requestBuilder, RateLimiter rateLimiter,
                  int connectTimeoutSeconds, int maxResponseLength) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.requestBuilder = requestBuilder;
        this.rateLimiter = rateLimiter;
        this.maxResponseLength = maxResponseLength;
    }

    String execute(String path, String method, Operation operation, Map<String, Object> args) {
        rateLimiter.checkAndRecord();

        try {
            HttpRequest request = requestBuilder.build(path, method, operation, args);
            log.debug("{} {}", method.toUpperCase(), request.uri());

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            log.debug("Response: {} ({} chars)", response.statusCode(), response.body().length());

            if (response.statusCode() >= 400) {
                throw new ApiException("Cloudflare API error (HTTP " + response.statusCode() + "): " +
                        ContentSanitizer.truncate(response.body(), 500));
            }

            return response.body();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("Request failed: " + e.getMessage(), e);
        }
    }

    int maxResponseLength() {
        return maxResponseLength;
    }

    static final class ApiException extends RuntimeException {
        ApiException(String message) {
            super(message);
        }

        ApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
