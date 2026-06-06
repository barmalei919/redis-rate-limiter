package barmalei919.ratelim.key;

import barmalei919.ratelim.domain.Identity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class IpKeyResolver implements KeyResolver {

    @Override
    public Identity identity() {
        return Identity.IP;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip;
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            ip = (comma >= 0 ? forwarded.substring(0, comma) : forwarded).trim();
        } else {
            ip = request.getRemoteAddr();
        }
        return "ip:" + ip;
    }
}
