package barmalei919.ratelim.core;

import barmalei919.ratelim.config.RateLimitProperties;
import barmalei919.ratelim.domain.Rule;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.Comparator;
import java.util.List;

@Component
public class RuleMatcher {

    private final List<Rule> orderedRules;
    private final Rule defaultRule;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public RuleMatcher(RateLimitProperties properties) {
        this.defaultRule = properties.defaultRule();
        this.orderedRules = properties.rules() == null
                ? List.of()
                : properties.rules().values().stream()
                        .sorted(Comparator.comparingInt((Rule r) -> specificity(r.pattern())).reversed())
                        .toList();
    }

    public Rule match(String path) {
        for (Rule rule : orderedRules) {
            if (matcher.match(rule.pattern(), path)) {
                return rule;
            }
        }
        return defaultRule;
    }

    private static int specificity(String pattern) {
        return pattern.replace("**", "").replace("*", "").length();
    }
}
