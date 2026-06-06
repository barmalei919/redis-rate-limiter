package barmalei919.ratelim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
@SpringBootApplication
@ConfigurationPropertiesScan
public class RateLimApplication {
    public static void main(String[] args) {
        SpringApplication.run(RateLimApplication.class, args);
    }
}
