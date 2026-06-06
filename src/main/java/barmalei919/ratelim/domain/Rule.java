package barmalei919.ratelim.domain;

public record Rule(
        String name,
        String pattern,
        Algorithm algorithm,
        Identity identity,
        int limit,
        long windowMs,
        Integer burstCapacity
) {
    public int effectiveBurstCapacity() {
        return burstCapacity != null ? burstCapacity : limit;
    }
}
