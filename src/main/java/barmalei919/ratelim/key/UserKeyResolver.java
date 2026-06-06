package barmalei919.ratelim.key;

import barmalei919.ratelim.domain.Identity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class UserKeyResolver implements KeyResolver {

    @Override
    public Identity identity() {
        return Identity.USER;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isBlank()) {
            return null;
        }
        return "user:" + userId.trim();
    }
}
