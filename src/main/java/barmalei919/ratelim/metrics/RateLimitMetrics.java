package barmalei919.ratelim.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class RateLimitMetrics {

    private final MeterRegistry registry;
    private final ConcurrentMap<String, Counter> allowedCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> deniedCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> failureCounters = new ConcurrentHashMap<>();

    public RateLimitMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(String ruleName, boolean allowed) {
        ConcurrentMap<String, Counter> cache = allowed ? allowedCounters : deniedCounters;
        String outcome = allowed ? "allowed" : "denied";
        cache.computeIfAbsent(ruleName, name -> Counter.builder("ratelimit.requests")
                        .tag("rule", name)
                        .tag("outcome", outcome)
                        .register(registry))
                .increment();
    }

    public void recordRedisFailure(String ruleName) {
        failureCounters.computeIfAbsent(ruleName, name -> Counter.builder("ratelimit.redis_failures")
                        .tag("rule", name)
                        .register(registry))
                .increment();
    }
}
