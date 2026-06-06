package barmalei919.ratelim.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class DemoController {

    @PostMapping("/auth/login")
    public Map<String, String> login() {
        return Map.of("status", "ok", "endpoint", "auth/login");
    }

    @GetMapping("/cheap/items")
    public Map<String, String> cheap() {
        return Map.of("status", "ok", "endpoint", "cheap/items");
    }

    @GetMapping("/expensive/report")
    public Map<String, String> expensive() {
        return Map.of("status", "ok", "endpoint", "expensive/report");
    }

    @GetMapping("/ping")
    public Map<String, String> ping() {
        return Map.of("status", "ok", "endpoint", "ping");
    }
}
