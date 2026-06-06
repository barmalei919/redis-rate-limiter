package barmalei919.ratelim.key;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class CompositeKeyResolverTest {

    private final CompositeKeyResolver resolver =
            new CompositeKeyResolver(new UserKeyResolver(), new IpKeyResolver());

    @Test
    void preferUserIdWhenPresent() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-User-Id", "42");
        req.setRemoteAddr("1.2.3.4");
        assertThat(resolver.resolve(req)).isEqualTo("user:42");
    }

    @Test
    void fallBackToRemoteAddrWhenNoUser() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("1.2.3.4");
        assertThat(resolver.resolve(req)).isEqualTo("ip:1.2.3.4");
    }

    @Test
    void useFirstForwardedIpWhenNoUser() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Forwarded-For", "10.0.0.1, 192.168.1.1");
        req.setRemoteAddr("1.2.3.4");
        assertThat(resolver.resolve(req)).isEqualTo("ip:10.0.0.1");
    }

    @Test
    void treatBlankUserAsAbsent() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-User-Id", "   ");
        req.setRemoteAddr("1.2.3.4");
        assertThat(resolver.resolve(req)).isEqualTo("ip:1.2.3.4");
    }
}
