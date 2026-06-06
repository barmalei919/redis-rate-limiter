package barmalei919.ratelim.key;

import barmalei919.ratelim.domain.Identity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class CompositeKeyResolver implements KeyResolver {

    private final UserKeyResolver user;
    private final IpKeyResolver ip;

    public CompositeKeyResolver(UserKeyResolver user, IpKeyResolver ip) {
        this.user = user;
        this.ip = ip;
    }

    @Override
    public Identity identity() {
        return Identity.COMPOSITE;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        String userKey = user.resolve(request);
        return userKey != null ? userKey : ip.resolve(request);
    }
}
