package az.millers.hcm.apikey.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * M120 — bucket math. The auth filter trusts these properties on every
 * inbound request:
 * <ul>
 *   <li>a fresh bucket starts full,</li>
 *   <li>{@link TokenBucket#tryConsume} returns false once the bucket
 *       drains and stays false until enough time has passed for the
 *       continuous refill to push it back above 1,</li>
 *   <li>{@link TokenBucket#retryAfterSeconds} returns 0 when a token is
 *       available and a positive ceiling-of-seconds value otherwise.</li>
 * </ul>
 */
class TokenBucketTest {

    private static final long ONE_SECOND_NANOS = 1_000_000_000L;

    @Test
    void freshBucketAllowsCapacityRequests() {
        TokenBucket b = TokenBucket.forTesting(5, 0);
        for (int i = 0; i < 5; i++) {
            assertThat(b.tryConsume(0)).as("request " + i).isTrue();
        }
        assertThat(b.tryConsume(0)).as("6th request should be rejected").isFalse();
    }

    @Test
    void refillsContinuouslyAcrossTime() {
        TokenBucket b = TokenBucket.forTesting(60, 0);
        // Drain.
        for (int i = 0; i < 60; i++) assertThat(b.tryConsume(0)).isTrue();
        assertThat(b.tryConsume(0)).isFalse();
        // 1 second elapsed → 1 token refilled (60/min = 1/s).
        assertThat(b.tryConsume(ONE_SECOND_NANOS)).isTrue();
        // Next request immediately should fail (just consumed our refill).
        assertThat(b.tryConsume(ONE_SECOND_NANOS)).isFalse();
    }

    @Test
    void capacityIsTheCeiling() {
        TokenBucket b = TokenBucket.forTesting(10, 0);
        // Idle 1 hour — bucket should still be at most 10, not 600.
        long oneHour = 3600 * ONE_SECOND_NANOS;
        for (int i = 0; i < 10; i++) {
            assertThat(b.tryConsume(oneHour)).as("request " + i).isTrue();
        }
        assertThat(b.tryConsume(oneHour)).as("11th request should be rejected").isFalse();
    }

    @Test
    void retryAfterIsZeroWhenTokenAvailable() {
        TokenBucket b = TokenBucket.forTesting(60, 0);
        assertThat(b.retryAfterSeconds(0)).isEqualTo(0);
    }

    @Test
    void retryAfterCountsUpFromEmpty() {
        TokenBucket b = TokenBucket.forTesting(60, 0);
        // Drain the bucket.
        for (int i = 0; i < 60; i++) b.tryConsume(0);
        // At 60/min, time to refill 1 token is 1 second.
        long retry = b.retryAfterSeconds(0);
        assertThat(retry).isGreaterThanOrEqualTo(1).isLessThanOrEqualTo(2);
    }

    @Test
    void evictRemovesGlobalBucket() {
        java.util.UUID id = java.util.UUID.randomUUID();
        TokenBucket b1 = TokenBucket.get(id, 5, 0);
        for (int i = 0; i < 5; i++) b1.tryConsume(0);
        assertThat(b1.tryConsume(0)).isFalse();
        TokenBucket.evict(id);
        // After evict, get() returns a fresh full bucket.
        TokenBucket b2 = TokenBucket.get(id, 5, 0);
        assertThat(b2.tryConsume(0)).isTrue();
    }

    @Test
    void resizingCapacityResetsBucket() {
        java.util.UUID id = java.util.UUID.randomUUID();
        TokenBucket b1 = TokenBucket.get(id, 3, 0);
        for (int i = 0; i < 3; i++) b1.tryConsume(0);
        // Admin tightened the limit — different capacity → new full bucket.
        TokenBucket b2 = TokenBucket.get(id, 10, 0);
        assertThat(b2).isNotSameAs(b1);
        for (int i = 0; i < 10; i++) {
            assertThat(b2.tryConsume(0)).as("after resize, request " + i).isTrue();
        }
    }
}
