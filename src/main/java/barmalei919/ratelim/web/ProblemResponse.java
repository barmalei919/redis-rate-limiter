package barmalei919.ratelim.web;

public record ProblemResponse(
        String type,
        String title,
        int status,
        String detail,
        long retryAfterSeconds
) {
    public static ProblemResponse rateLimited(long retryAfterSeconds) {
        return new ProblemResponse(
                "https://datatracker.ietf.org/doc/html/rfc6585#section-4",
                "Too Many Requests",
                429,
                "Rate limit exceeded",
                retryAfterSeconds
        );
    }
}
