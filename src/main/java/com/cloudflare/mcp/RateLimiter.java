package com.cloudflare.mcp;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedDeque;

final class RateLimiter {

    private final int maxPerMinute;
    private final ConcurrentLinkedDeque<Instant> timestamps = new ConcurrentLinkedDeque<>();

    RateLimiter(int maxPerMinute) {
        this.maxPerMinute = maxPerMinute;
    }

    RateLimiter() {
        this(240);
    }

    void checkAndRecord() {
        Instant now = Instant.now();
        purgeOld(now);

        long lastMinute = timestamps.stream()
                .filter(t -> Duration.between(t, now).toSeconds() < 60)
                .count();
        if (lastMinute >= maxPerMinute) {
            throw new RateLimitExceededException(
                    "Rate limit exceeded: %d requests in the last minute (max %d). Wait before retrying."
                            .formatted(lastMinute, maxPerMinute));
        }

        timestamps.addLast(now);
    }

    private void purgeOld(Instant now) {
        while (!timestamps.isEmpty()) {
            Instant oldest = timestamps.peekFirst();
            if (oldest != null && Duration.between(oldest, now).toSeconds() >= 60) {
                timestamps.pollFirst();
            } else {
                break;
            }
        }
    }

    int getRequestCountLastMinute() {
        Instant now = Instant.now();
        purgeOld(now);
        return (int) timestamps.stream()
                .filter(t -> Duration.between(t, now).toSeconds() < 60)
                .count();
    }

    static final class RateLimitExceededException extends RuntimeException {
        RateLimitExceededException(String message) {
            super(message);
        }
    }
}
