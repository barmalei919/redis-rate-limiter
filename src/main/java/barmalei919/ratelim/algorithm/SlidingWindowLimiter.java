package barmalei919.ratelim.algorithm;

import barmalei919.ratelim.domain.Decision;
import barmalei919.ratelim.domain.Rule;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class SlidingWindowLimiter implements RateLimiter {

    private final StringRedisTemplate redis;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> script;

    public SlidingWindowLimiter(StringRedisTemplate redis,
                                @Qualifier("slidingWindowScript") @SuppressWarnings("rawtypes") RedisScript<List> script) {
        this.redis = redis;
        this.script = script;
    }

    @Override
    public Decision tryAcquire(String key, Rule rule) {
        List<?> result = redis.execute(
                script,
                List.of(key),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(rule.windowMs()),
                String.valueOf(rule.limit()),
                UUID.randomUUID().toString()
        );
        long allowed = ((Number) result.get(0)).longValue();
        long remaining = ((Number) result.get(1)).longValue();
        long retryAfterMs = ((Number) result.get(2)).longValue();
        return allowed == 1
                ? Decision.allowed(remaining, rule.limit())
                : Decision.denied(retryAfterMs, rule.limit());
    }
}
