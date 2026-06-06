package barmalei919.ratelim.core;

import barmalei919.ratelim.domain.Identity;
import barmalei919.ratelim.key.KeyResolver;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class KeyResolverRegistry {

    private final Map<Identity, KeyResolver> resolvers;

    public KeyResolverRegistry(List<KeyResolver> all) {
        this.resolvers = all.stream().collect(Collectors.toMap(KeyResolver::identity, Function.identity()));
    }

    public KeyResolver forIdentity(Identity identity) {
        KeyResolver resolver = resolvers.get(identity);
        if (resolver == null) {
            throw new IllegalStateException("No resolver for identity " + identity);
        }
        return resolver;
    }
}
