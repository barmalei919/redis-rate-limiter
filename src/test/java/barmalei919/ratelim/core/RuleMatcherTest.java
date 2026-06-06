package barmalei919.ratelim.core;

import barmalei919.ratelim.config.RateLimitProperties;
import barmalei919.ratelim.domain.Algorithm;
import barmalei919.ratelim.domain.Identity;
import barmalei919.ratelim.domain.Rule;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuleMatcherTest {

    private static final Rule DEFAULT_RULE =
            new Rule("default", "/**", Algorithm.SLIDING_WINDOW, Identity.COMPOSITE, 60, 60_000, null);

    @Test
    void picksRuleByPattern() {
        Rule auth = new Rule("auth", "/api/auth/**", Algorithm.SLIDING_WINDOW, Identity.IP, 5, 60_000, null);
        Rule cheap = new Rule("cheap", "/api/cheap/**", Algorithm.TOKEN_BUCKET, Identity.COMPOSITE, 100, 60_000, 150);

        RuleMatcher matcher = new RuleMatcher(
                new RateLimitProperties(true, DEFAULT_RULE, Map.of("auth", auth, "cheap", cheap)));

        assertThat(matcher.match("/api/auth/login").name()).isEqualTo("auth");
        assertThat(matcher.match("/api/cheap/items").name()).isEqualTo("cheap");
        assertThat(matcher.match("/api/other").name()).isEqualTo("default");
    }

    @Test
    void prefersMoreSpecificPattern() {
        Rule generic = new Rule("generic", "/api/**", Algorithm.SLIDING_WINDOW, Identity.IP, 100, 60_000, null);
        Rule specific = new Rule("specific", "/api/auth/**", Algorithm.SLIDING_WINDOW, Identity.IP, 5, 60_000, null);

        RuleMatcher matcher = new RuleMatcher(
                new RateLimitProperties(true, DEFAULT_RULE, Map.of("generic", generic, "specific", specific)));

        assertThat(matcher.match("/api/auth/login").name()).isEqualTo("specific");
    }

    @Test
    void fallsBackToDefaultWhenNoMatch() {
        Rule auth = new Rule("auth", "/api/auth/**", Algorithm.SLIDING_WINDOW, Identity.IP, 5, 60_000, null);

        RuleMatcher matcher = new RuleMatcher(
                new RateLimitProperties(true, DEFAULT_RULE, Map.of("auth", auth)));

        assertThat(matcher.match("/something/else").name()).isEqualTo("default");
    }

    @Test
    void handlesEmptyRules() {
        RuleMatcher matcher = new RuleMatcher(new RateLimitProperties(true, DEFAULT_RULE, Map.of()));
        assertThat(matcher.match("/anything").name()).isEqualTo("default");
    }
}
