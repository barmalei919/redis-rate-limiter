package barmalei919.ratelim.web;

import barmalei919.ratelim.core.RateLimitService;
import barmalei919.ratelim.domain.Decision;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService service;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimitService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        RateLimitService.Outcome outcome = service.check(request);
        Decision decision = outcome.decision();

        response.setHeader("X-RateLimit-Rule", outcome.rule().name());
        response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, decision.remaining())));

        if (!decision.allowed()) {
            long retryAfterSeconds = Math.max(1, (decision.retryAfterMs() + 999) / 1000);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), ProblemResponse.rateLimited(retryAfterSeconds));
            return;
        }

        chain.doFilter(request, response);
    }
}
