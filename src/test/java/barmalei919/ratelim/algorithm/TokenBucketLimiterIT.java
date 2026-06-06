package barmalei919.ratelim.algorithm;

import barmalei919.ratelim.AbstractRedisIT;
import barmalei919.ratelim.domain.Algorithm;
import barmalei919.ratelim.domain.Decision;
import barmalei919.ratelim.domain.Identity;
import barmalei919.ratelim.domain.Rule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBucketLimiterIT extends AbstractRedisIT {

    @Autowired
    TokenBucketLimiter limiter;

    @Test
    void burstsUpToCapacity() {
        String key = "test:" + UUID.randomUUID();
        Rule rule = new Rule("t", "/**", Algorithm.TOKEN_BUCKET, Identity.IP, 10, 60_000, 10);

        for (int i = 0; i < 10; i++) {
            assertThat(limiter.tryAcquire(key, rule).allowed()).isTrue();
        }
        assertThat(limiter.tryAcquire(key, rule).allowed()).isFalse();
    }

    @Test
    void allowsHigherBurstThanSteadyRate() {
        String key = "test:" + UUID.randomUUID();
        Rule rule = new Rule("t", "/**", Algorithm.TOKEN_BUCKET, Identity.IP, 10, 60_000, 20);

        int allowed = 0;
        for (int i = 0; i < 25; i++) {
            if (limiter.tryAcquire(key, rule).allowed()) {
                allowed++;
            }
        }
        assertThat(allowed).isGreaterThanOrEqualTo(20).isLessThan(25);
    }

    @Test
    void refillsOverTime() throws InterruptedException {
        String key = "test:" + UUID.randomUUID();
        Rule rule = new Rule("t", "/**", Algorithm.TOKEN_BUCKET, Identity.IP, 1000, 1000, 5);

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire(key, rule).allowed()).isTrue();
        }
        Decision denied = limiter.tryAcquire(key, rule);
        assertThat(denied.allowed()).isFalse();

        Thread.sleep(50);
        assertThat(limiter.tryAcquire(key, rule).allowed()).isTrue();
    }

    @Test
    void retryAfterReflectsRefillTime() {
        String key = "test:" + UUID.randomUUID();
        Rule rule = new Rule("t", "/**", Algorithm.TOKEN_BUCKET, Identity.IP, 10, 1000, 1);

        assertThat(limiter.tryAcquire(key, rule).allowed()).isTrue();
        Decision denied = limiter.tryAcquire(key, rule);
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterMs()).isBetween(50L, 200L);
    }
}
