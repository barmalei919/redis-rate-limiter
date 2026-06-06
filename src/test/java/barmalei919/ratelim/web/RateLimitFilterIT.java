package barmalei919.ratelim.web;

import barmalei919.ratelim.AbstractRedisIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RateLimitFilterIT extends AbstractRedisIT {

    @Autowired
    TestRestTemplate http;

    @LocalServerPort
    int port;

    @Test
    void authEndpointBlocksAfterFiveTries() {
        String url = base() + "/api/auth/login";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Forwarded-For", randomIp());

        for (int i = 0; i < 5; i++) {
            ResponseEntity<Map> r = http.exchange(url, HttpMethod.POST, new HttpEntity<>(headers), Map.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        ResponseEntity<Map> denied = http.exchange(url, HttpMethod.POST, new HttpEntity<>(headers), Map.class);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(denied.getHeaders().getFirst("Retry-After")).isNotNull();
        assertThat(denied.getHeaders().getFirst("X-RateLimit-Rule")).isEqualTo("auth");
    }

    @Test
    void differentUsersHaveIndependentBuckets() {
        String url = base() + "/api/expensive/report";
        HttpHeaders alice = new HttpHeaders();
        alice.set("X-User-Id", "alice-" + UUID.randomUUID());
        HttpHeaders bob = new HttpHeaders();
        bob.set("X-User-Id", "bob-" + UUID.randomUUID());

        for (int i = 0; i < 5; i++) {
            assertThat(get(url, alice).getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        assertThat(get(url, alice).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(get(url, bob).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void deniedResponseIsProblemJson() {
        String url = base() + "/api/auth/login";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Forwarded-For", randomIp());

        for (int i = 0; i < 5; i++) {
            http.exchange(url, HttpMethod.POST, new HttpEntity<>(headers), String.class);
        }
        ResponseEntity<Map> denied = http.exchange(url, HttpMethod.POST, new HttpEntity<>(headers), Map.class);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(denied.getHeaders().getContentType().toString()).contains("application/problem+json");
        assertThat(denied.getBody()).containsKey("type").containsKey("title").containsKey("status");
    }

    @Test
    void actuatorEndpointsAreNotRateLimited() {
        String url = base() + "/actuator/health";
        for (int i = 0; i < 30; i++) {
            ResponseEntity<Map> r = http.getForEntity(url, Map.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    private ResponseEntity<Map> get(String url, HttpHeaders headers) {
        return http.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }

    private String base() {
        return "http://localhost:" + port;
    }

    private static String randomIp() {
        return "10.0." + (int) (Math.random() * 255) + "." + (int) (Math.random() * 255);
    }
}
