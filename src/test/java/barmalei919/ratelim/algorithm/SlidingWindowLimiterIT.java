package barmalei919.ratelim.algorithm;

import barmalei919.ratelim.AbstractRedisIT;
import barmalei919.ratelim.domain.Algorithm;
import barmalei919.ratelim.domain.Decision;
import barmalei919.ratelim.domain.Identity;
import barmalei919.ratelim.domain.Rule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SlidingWindowLimiterIT extends AbstractRedisIT {

    @Autowired
    SlidingWindowLimiter limiter;

    @Test
    void allowsRequestsUpToLimit() {
        String key = "test:" + UUID.randomUUID();
        Rule rule = new Rule("t", "/**", Algorithm.SLIDING_WINDOW, Identity.IP, 5, 60_000, null);

        for (int i = 0; i < 5; i++) {
            Decision d = limiter.tryAcquire(key, rule);
            assertThat(d.allowed()).isTrue();
            assertThat(d.remaining()).isEqualTo(4 - i);
        }
    }

    @Test
    void deniesOnceLimitReached() {
        String key = "test:" + UUID.randomUUID();
        Rule rule = new Rule("t", "/**", Algorithm.SLIDING_WINDOW, Identity.IP, 3, 60_000, null);

        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire(key, rule).allowed()).isTrue();
        }
        Decision denied = limiter.tryAcquire(key, rule);
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterMs()).isPositive();
    }

    @Test
    void recoversAfterWindow() throws InterruptedException {
        String key = "test:" + UUID.randomUUID();
        Rule rule = new Rule("t", "/**", Algorithm.SLIDING_WINDOW, Identity.IP, 2, 500, null);

        assertThat(limiter.tryAcquire(key, rule).allowed()).isTrue();
        assertThat(limiter.tryAcquire(key, rule).allowed()).isTrue();
        assertThat(limiter.tryAcquire(key, rule).allowed()).isFalse();

        Thread.sleep(600);
        assertThat(limiter.tryAcquire(key, rule).allowed()).isTrue();
    }

    @Test
    void stayAtomicUnderConcurrency() throws InterruptedException {
        String key = "test:" + UUID.randomUUID();
        Rule rule = new Rule("t", "/**", Algorithm.SLIDING_WINDOW, Identity.IP, 50, 60_000, null);

        int threads = 20;
        int perThread = 10;
        AtomicInteger allowed = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        if (limiter.tryAcquire(key, rule).allowed()) {
                            allowed.incrementAndGet();
                        }
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(allowed.get()).isEqualTo(50);
    }
}
