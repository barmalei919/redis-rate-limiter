package barmalei919.ratelim.config;

import barmalei919.ratelim.domain.Rule;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "ratelimit")
public record RateLimitProperties(
        boolean failOpen,
        Rule defaultRule,
        Map<String, Rule> rules
) {}
