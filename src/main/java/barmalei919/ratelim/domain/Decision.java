package barmalei919.ratelim.domain;

public record Decision(
        boolean allowed,
        long remaining,
        long retryAfterMs,
        int limit
) {
    public static Decision allowed(long remaining, int limit) {
        return new Decision(true, remaining, 0, limit);
    }

    public static Decision denied(long retryAfterMs, int limit) {
        return new Decision(false, 0, retryAfterMs, limit);
    }
}
