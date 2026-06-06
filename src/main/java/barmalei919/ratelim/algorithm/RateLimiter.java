package barmalei919.ratelim.algorithm;

import barmalei919.ratelim.domain.Decision;
import barmalei919.ratelim.domain.Rule;

public interface RateLimiter {
    Decision tryAcquire(String key, Rule rule);
}
