package barmalei919.ratelim.core;

import barmalei919.ratelim.algorithm.RateLimiter;
import barmalei919.ratelim.algorithm.SlidingWindowLimiter;
import barmalei919.ratelim.algorithm.TokenBucketLimiter;
import barmalei919.ratelim.domain.Algorithm;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RateLimiterRegistry {

    private final Map<Algorithm, RateLimiter> limiters;

    public RateLimiterRegistry(SlidingWindowLimiter sliding, TokenBucketLimiter bucket) {
        this.limiters = Map.of(
                Algorithm.SLIDING_WINDOW, sliding,
                Algorithm.TOKEN_BUCKET, bucket
        );
    }

    public RateLimiter forAlgorithm(Algorithm algorithm) {
        RateLimiter limiter = limiters.get(algorithm);
        if (limiter == null) {
            throw new IllegalStateException("No limiter for algorithm " + algorithm);
        }
        return limiter;
    }
}
