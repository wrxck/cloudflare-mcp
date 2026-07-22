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
    class Concurrency {

        @Test
        void concurrent_callers_never_exceed_limit() throws Exception {
            final int limit = 50;
            final int threads = 200;
            RateLimiter limiter = new RateLimiter(limit);
            var barrier = new java.util.concurrent.CyclicBarrier(threads);
            var admitted = new java.util.concurrent.atomic.AtomicInteger();
            var pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
            try {
                var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
                for (int i = 0; i < threads; i++) {
                    futures.add(pool.submit(() -> {
                        try {
                            barrier.await();
                            limiter.checkAndRecord();
                            admitted.incrementAndGet();
                        } catch (RateLimiter.RateLimitExceededException expected) {
                            // over-limit callers are rejected
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }));
                }
                for (var f : futures) f.get();
            } finally {
                pool.shutdownNow();
            }
            assertTrue(admitted.get() <= limit,
                    "admitted " + admitted.get() + " requests, limit is " + limit);
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
    class WindowExpiry {

        @Test
        void old_requests_fall_out_of_the_window() {
            var clock = new java.util.concurrent.atomic.AtomicReference<>(
                    java.time.Instant.parse("2026-01-01T00:00:00Z"));
            RateLimiter limiter = new RateLimiter(2, () -> clock.get());

            limiter.checkAndRecord();
            limiter.checkAndRecord();
            assertThrows(RateLimiter.RateLimitExceededException.class, limiter::checkAndRecord);

            // one minute later the window is empty again
            clock.set(clock.get().plusSeconds(60));
            assertEquals(0, limiter.getRequestCountLastMinute());
            assertDoesNotThrow(limiter::checkAndRecord);
        }

        @Test
        void requests_inside_window_still_counted() {
            var clock = new java.util.concurrent.atomic.AtomicReference<>(
                    java.time.Instant.parse("2026-01-01T00:00:00Z"));
            RateLimiter limiter = new RateLimiter(2, () -> clock.get());

            limiter.checkAndRecord();
            clock.set(clock.get().plusSeconds(59));
            assertEquals(1, limiter.getRequestCountLastMinute());
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
