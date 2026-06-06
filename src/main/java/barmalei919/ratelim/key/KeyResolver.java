package barmalei919.ratelim.key;

import barmalei919.ratelim.domain.Identity;
import jakarta.servlet.http.HttpServletRequest;

public interface KeyResolver {
    Identity identity();
    String resolve(HttpServletRequest request);
}
