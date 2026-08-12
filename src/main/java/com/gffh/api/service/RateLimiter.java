package com.gffh.api.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A fixed-window (per clock hour) request counter, in-memory since this API
 * runs as a single instance. Good enough to blunt brute-forcing and
 * invitation spam without adding an external dependency; a multi-instance
 * deployment would need a shared store (e.g. Redis) instead.
 */
@Component
public class RateLimiter {

    private record Window(long hourEpoch, AtomicInteger count) {}

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    /** Returns true if this call is within the limit for {@code key} this hour, and counts it either way. */
    public boolean tryConsume(String key, int limitPerHour) {
        long currentHour = Instant.now().getEpochSecond() / 3600;
        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || existing.hourEpoch() != currentHour) {
                return new Window(currentHour, new AtomicInteger(0));
            }
            return existing;
        });
        return window.count().incrementAndGet() <= limitPerHour;
    }
}
