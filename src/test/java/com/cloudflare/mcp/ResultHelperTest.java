package com.cloudflare.mcp;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResultHelperTest {

    @Nested
    class SanitizedResult {

        @Test
        void returns_two_text_contents() {
            CallToolResult result = ResultHelper.sanitizedResult("{\"ok\":true}", 50000);
            assertEquals(2, result.content().size());
        }

        @Test
        void first_content_is_security_context() {
            CallToolResult result = ResultHelper.sanitizedResult("{\"ok\":true}", 50000);
            String first = result.content().get(0).toString();
            assertTrue(first.contains("UNTRUSTED") || first.contains("SECURITY"));
        }

        @Test
        void is_not_error() {
            CallToolResult result = ResultHelper.sanitizedResult("{\"ok\":true}", 50000);
            assertFalse(result.isError());
        }

        @Test
        void oversized_body_truncated_exactly_once() {
            CallToolResult result = ResultHelper.sanitizedResult("x".repeat(200), 100);
            String wrapped = result.content().get(1).toString();
            int markers = 0;
            int idx = 0;
            while ((idx = wrapped.indexOf("[truncated at", idx)) != -1) {
                markers++;
                idx++;
            }
            assertEquals(1, markers,
                    "truncation marker must appear exactly once, content: " + wrapped);
        }
    }

    @Nested
    class ErrorResult {

        @Test
        void returns_single_content() {
            CallToolResult result = ResultHelper.errorResult("something failed");
            assertEquals(1, result.content().size());
        }

        @Test
        void is_error() {
            CallToolResult result = ResultHelper.errorResult("something failed");
            assertTrue(result.isError());
        }
    }
}
