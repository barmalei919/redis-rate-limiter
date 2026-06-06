package barmalei919.ratelim.core;

import barmalei919.ratelim.algorithm.RateLimiter;
import barmalei919.ratelim.config.RateLimitProperties;
import barmalei919.ratelim.domain.Decision;
import barmalei919.ratelim.domain.Identity;
import barmalei919.ratelim.domain.Rule;
import barmalei919.ratelim.key.KeyResolver;
import barmalei919.ratelim.metrics.RateLimitMetrics;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private final RuleMatcher matcher;
    private final KeyResolverRegistry resolvers;
    private final RateLimiterRegistry limiters;
    private final RateLimitMetrics metrics;
    private final boolean failOpen;

    public RateLimitService(RuleMatcher matcher,
                            KeyResolverRegistry resolvers,
                            RateLimiterRegistry limiters,
                            RateLimitMetrics metrics,
                            RateLimitProperties properties) {
        this.matcher = matcher;
        this.resolvers = resolvers;
        this.limiters = limiters;
        this.metrics = metrics;
        this.failOpen = properties.failOpen();
    }

    public Outcome check(HttpServletRequest request) {
        Rule rule = matcher.match(request.getRequestURI());
        KeyResolver resolver = resolvers.forIdentity(rule.identity());
        String identityKey = resolver.resolve(request);
        if (identityKey == null) {
            identityKey = resolvers.forIdentity(Identity.IP).resolve(request);
        }
        String key = "rl:" + rule.name() + ":" + identityKey;
        RateLimiter limiter = limiters.forAlgorithm(rule.algorithm());

        try {
            Decision decision = limiter.tryAcquire(key, rule);
            metrics.record(rule.name(), decision.allowed());
            return new Outcome(rule, decision);
        } catch (DataAccessException e) {
            metrics.recordRedisFailure(rule.name());
            if (failOpen) {
                log.warn("rate limiter dependency failure for rule={}, allowing request", rule.name(), e);
                return new Outcome(rule, Decision.allowed(-1, rule.limit()));
            }
            throw e;
        }
    }

    public record Outcome(Rule rule, Decision decision) {}
}
