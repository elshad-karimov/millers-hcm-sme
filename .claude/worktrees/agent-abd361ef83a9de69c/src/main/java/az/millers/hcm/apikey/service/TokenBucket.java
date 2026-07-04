package az.millers.hcm.apikey.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * M120 — in-process token bucket for per-key rate limiting. One bucket
 * per {@link java.util.UUID} api-key-id; each {@link #tryConsume} returns
 * whether a token was available "now" and refills lazily at a steady
 * rate.
 *
 * <p>Algorithm: continuous refill — capacity = burst size = rate per
 * minute, refill rate = capacity / 60s. {@link #tryConsume} computes the
 * current token count from the timestamp of the last update, deducts one
 * if available, and CASs the bucket state. Concurrent calls race through
 * the CAS, so under contention some callers retry — bounded by
 * {@value #CAS_RETRIES} attempts before failing closed.
 *
 * <p>Pure-static API on the class side; per-key state lives in an
 * internal map. Tests instantiate one bucket via {@link #forTesting}
 * and step a clock by hand.
 */
public final class TokenBucket {

    /** Max CAS retries before a request is denied to break livelock under contention. */
    static final int CAS_RETRIES = 8;

    /** Internal mutable state — one record per bucket. */
    record Snapshot(double tokens, long epochNanos) {}

    private static final ConcurrentHashMap<java.util.UUID, TokenBucket> BUCKETS = new ConcurrentHashMap<>();

    private final double capacity;
    /** Tokens added per nanosecond. */
    private final double refillPerNano;
    private final AtomicReference<Snapshot> state;

    private TokenBucket(double capacity, long startNanos) {
        this.capacity = capacity;
        // capacity tokens per 60 seconds.
        this.refillPerNano = capacity / 60_000_000_000.0;
        this.state = new AtomicReference<>(new Snapshot(capacity, startNanos));
    }

    /**
     * Returns the bucket for {@code keyId} with capacity {@code ratePerMin},
     * creating one if absent. If the capacity changed (the admin tightened
     * or relaxed the limit), the bucket is reset to a fresh full state —
     * a one-off generosity that's preferable to either leaking the new
     * cap or starving the caller.
     */
    public static TokenBucket get(java.util.UUID keyId, int ratePerMin, long nowNanos) {
        TokenBucket existing = BUCKETS.get(keyId);
        if (existing != null && Double.compare(existing.capacity, ratePerMin) == 0) {
            return existing;
        }
        TokenBucket fresh = new TokenBucket(ratePerMin, nowNanos);
        BUCKETS.put(keyId, fresh);
        return fresh;
    }

    /** For unit tests — no global state. */
    public static TokenBucket forTesting(int ratePerMin, long startNanos) {
        return new TokenBucket(ratePerMin, startNanos);
    }

    /** Force-evict a bucket (use after a revoke so the next valid key starts fresh). */
    public static void evict(java.util.UUID keyId) {
        BUCKETS.remove(keyId);
    }

    /**
     * Try to consume one token "now". Returns true iff a token was
     * available. CAS-loops up to {@value #CAS_RETRIES} times under
     * contention; gives up otherwise to avoid livelock.
     */
    public boolean tryConsume(long nowNanos) {
        for (int attempt = 0; attempt < CAS_RETRIES; attempt++) {
            Snapshot s = state.get();
            double elapsed = Math.max(0, nowNanos - s.epochNanos());
            double refilled = Math.min(capacity, s.tokens() + elapsed * refillPerNano);
            if (refilled < 1.0) {
                // Update the timestamp anyway so the next call sees less elapsed work.
                Snapshot updated = new Snapshot(refilled, nowNanos);
                state.compareAndSet(s, updated);
                return false;
            }
            Snapshot updated = new Snapshot(refilled - 1.0, nowNanos);
            if (state.compareAndSet(s, updated)) return true;
        }
        return false;
    }

    /** Seconds until ≥1 token is available — used for the {@code Retry-After} header. */
    public long retryAfterSeconds(long nowNanos) {
        Snapshot s = state.get();
        double elapsed = Math.max(0, nowNanos - s.epochNanos());
        double refilled = Math.min(capacity, s.tokens() + elapsed * refillPerNano);
        if (refilled >= 1.0) return 0;
        double deficit = 1.0 - refilled;
        double nanosNeeded = deficit / refillPerNano;
        return (long) Math.ceil(nanosNeeded / 1_000_000_000.0);
    }
}
