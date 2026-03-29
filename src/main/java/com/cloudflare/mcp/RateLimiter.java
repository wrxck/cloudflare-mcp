package com.cloudflare.mcp;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class RateLimiter {

    private final int maxPerMinute;
    private final ConcurrentLinkedDeque<Instant> timestamps = new ConcurrentLinkedDeque<>();

    public RateLimiter(int maxPerMinute) {
        this.maxPerMinute = maxPerMinute;
    }

    public RateLimiter() {
        this(240);
    }

    public void checkAndRecord() {
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

    public int getRequestCountLastMinute() {
        Instant now = Instant.now();
        purgeOld(now);
        return (int) timestamps.stream()
                .filter(t -> Duration.between(t, now).toSeconds() < 60)
                .count();
    }

    public static final class RateLimitExceededException extends RuntimeException {
        public RateLimitExceededException(String message) {
            super(message);
        }
    }
}
