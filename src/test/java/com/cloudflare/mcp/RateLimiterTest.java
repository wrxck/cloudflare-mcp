package com.cloudflare.mcp;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    @Nested
    class CheckAndRecord {

        @Test
        void allows_requests_under_limit() {
            RateLimiter limiter = new RateLimiter(10);
            assertDoesNotThrow(() -> {
                for (int i = 0; i < 10; i++) {
                    limiter.checkAndRecord();
                }
            });
        }

        @Test
        void throws_when_limit_exceeded() {
            RateLimiter limiter = new RateLimiter(5);
            for (int i = 0; i < 5; i++) {
                limiter.checkAndRecord();
            }
            assertThrows(RateLimiter.RateLimitExceededException.class,
                    limiter::checkAndRecord);
        }

        @Test
        void exception_message_contains_counts() {
            RateLimiter limiter = new RateLimiter(2);
            limiter.checkAndRecord();
            limiter.checkAndRecord();

            var ex = assertThrows(RateLimiter.RateLimitExceededException.class,
                    limiter::checkAndRecord);
            assertTrue(ex.getMessage().contains("2"));
            assertTrue(ex.getMessage().contains("max 2"));
        }
    }

    @Nested
    class GetRequestCount {

        @Test
        void zero_initially() {
            RateLimiter limiter = new RateLimiter(10);
            assertEquals(0, limiter.getRequestCountLastMinute());
        }

        @Test
        void tracks_requests() {
            RateLimiter limiter = new RateLimiter(10);
            limiter.checkAndRecord();
            limiter.checkAndRecord();
            limiter.checkAndRecord();
            assertEquals(3, limiter.getRequestCountLastMinute());
        }
    }

    @Nested
    class DefaultLimit {

        @Test
        void default_is_240() {
            RateLimiter limiter = new RateLimiter();
            // Should allow at least 200 requests
            assertDoesNotThrow(() -> {
                for (int i = 0; i < 200; i++) {
                    limiter.checkAndRecord();
                }
            });
        }
    }
}
