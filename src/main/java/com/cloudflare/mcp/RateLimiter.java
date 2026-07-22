package com.cloudflare.mcp;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayDeque;
import java.util.Deque;

public final class RateLimiter {

    private final int maxPerMinute;
    private final InstantSource clock;
    private final Deque<Instant> timestamps = new ArrayDeque<>();

    public RateLimiter(int maxPerMinute) {
        this(maxPerMinute, InstantSource.system());
    }

    public RateLimiter() {
        this(240);
    }

    /** Test-only constructor allowing the clock to be overridden. */
    RateLimiter(int maxPerMinute, InstantSource clock) {
        this.maxPerMinute = maxPerMinute;
        this.clock = clock;
    }

    public synchronized void checkAndRecord() {
        Instant now = clock.instant();
        purgeOld(now);

        if (timestamps.size() >= maxPerMinute) {
            throw new RateLimitExceededException(
                    "Rate limit exceeded: %d requests in the last minute (max %d). Wait before retrying."
                            .formatted(timestamps.size(), maxPerMinute));
        }

        timestamps.addLast(now);
    }

    /** Removes timestamps older than one minute; everything remaining is in-window. */
    private void purgeOld(Instant now) {
        while (!timestamps.isEmpty()
                && Duration.between(timestamps.peekFirst(), now).toSeconds() >= 60) {
            timestamps.pollFirst();
        }
    }

    public synchronized int getRequestCountLastMinute() {
        purgeOld(clock.instant());
        return timestamps.size();
    }

    public static final class RateLimitExceededException extends RuntimeException {
        public RateLimitExceededException(String message) {
            super(message);
        }
    }
}
