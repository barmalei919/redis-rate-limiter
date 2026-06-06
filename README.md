# redis-rate-limiter

Distributed HTTP rate limiter built on Spring Boot and Redis. Supports two algorithms (sliding window log, token bucket), per-route rules, multi-tier identity (IP / userId / composite), Prometheus metrics, and graceful degradation when Redis is unavailable.

---

## Why two algorithms

| Algorithm | When it fits | Trade-offs |
|---|---|---|
| **Sliding window log** | Strict quotas (e.g. login attempts, password reset) where bursts must never exceed the configured limit | Memory grows with the number of in-window requests; not bursty |
| **Token bucket** | Regular API endpoints that should tolerate short bursts but average out to a steady rate | Bursty by design; needs separate `burstCapacity` and steady rate |

`/api/auth/**` uses sliding window because we never want to let an attacker get 6 login attempts in a minute, even briefly. `/api/cheap/**` and `/api/expensive/**` use token bucket because real users sometimes click fast, and rejecting them on millisecond-clean window boundaries hurts UX.

---

## Why atomic Lua

A naive `INCR + EXPIRE` pair is two round trips. Between them, two parallel clients can both read `count = 9`, both decide they're under the limit of 10, both increment, and the limit is breached. This is the classic TOCTOU race.

Redis Lua scripts run as a single atomic unit — no other command interleaves between `ZCARD` and `ZADD`. Both `sliding_window.lua` and `token_bucket.lua` package the entire check-and-mutate into one script.

---

## Architecture

```
HTTP request
   │
   ▼
RateLimitFilter ── skips /actuator/**
   │
   ▼
RateLimitService
   │
   ├── RuleMatcher        (path → Rule, most specific pattern wins)
   ├── KeyResolverRegistry (Rule.identity → KeyResolver → identityKey)
   ├── RateLimiterRegistry (Rule.algorithm → RateLimiter → Decision)
   └── RateLimitMetrics    (allowed/denied/redis_failure counters)
   │
   ▼
Decision  ──►  X-RateLimit-Limit, X-RateLimit-Remaining, X-RateLimit-Rule
   │
   ▼ if denied
429 + Retry-After + application/problem+json body
```

Key Redis data:

- **Sliding window** — `ZSET` per (rule, identity). Score = request timestamp ms, member = UUID. `ZREMRANGEBYSCORE` evicts expired entries, `ZCARD` counts the live window.
- **Token bucket** — `HASH` per (rule, identity) with two fields: `t` (current tokens, fractional), `ts` (last refill timestamp). Tokens accrue linearly between calls.

Both keys get `PEXPIRE` set on every call so idle keys self-clean.

---

## Configuration

`application.yml`:

```yaml
ratelimit:
  fail-open: true
  default-rule:
    name: default
    pattern: /**
    algorithm: SLIDING_WINDOW
    identity: COMPOSITE
    limit: 60
    window-ms: 60000
  rules:
    auth:
      pattern: /api/auth/**
      algorithm: SLIDING_WINDOW
      identity: IP
      limit: 5
      window-ms: 60000
    cheap:
      pattern: /api/cheap/**
      algorithm: TOKEN_BUCKET
      identity: COMPOSITE
      limit: 100
      window-ms: 60000
      burst-capacity: 150
    expensive:
      pattern: /api/expensive/**
      algorithm: TOKEN_BUCKET
      identity: USER
      limit: 5
      window-ms: 60000
      burst-capacity: 5
```

`fail-open: true` — if Redis is unreachable, requests pass through. A `ratelimit_redis_failures_total` counter increments so the team can alert on it. Set to `false` to fail closed (return 503).

`identity` values:
- `IP` — `X-Forwarded-For` (first hop) or `RemoteAddr`
- `USER` — `X-User-Id` header (falls back to IP if missing)
- `COMPOSITE` — user if header is present, else IP

---

## Running

### Local

```bash
docker compose up -d redis
./mvnw spring-boot:run
curl -i -X POST http://localhost:8080/api/auth/login
```

Sixth call within 60 seconds returns:

```
HTTP/1.1 429 Too Many Requests
Retry-After: 47
X-RateLimit-Rule: auth
X-RateLimit-Limit: 5
X-RateLimit-Remaining: 0
Content-Type: application/problem+json

{"type":"https://datatracker.ietf.org/doc/html/rfc6585#section-4","title":"Too Many Requests","status":429,"detail":"Rate limit exceeded","retryAfterSeconds":47}
```

### Full stack with metrics

```bash
docker compose up -d
./mvnw spring-boot:run
```

- App: http://localhost:8080
- Actuator metrics: http://localhost:8080/actuator/prometheus
- Prometheus UI: http://localhost:9090
- Grafana: http://localhost:3000 (anonymous viewer, or admin/admin)

The "Rate Limiter" dashboard auto-loads in Grafana with allowed/denied rates, Redis failure rate, and denial percentage by rule.

### Load test

Requires [k6](https://k6.io/):

```bash
k6 run k6/load-test.js
```

Generates three concurrent traffic profiles (cheap, expensive, auth) for one minute. Watch the Grafana dashboard during the run — denial lines settle right at the configured limits.

---

## Endpoints

| Method | Path | Rule |
|---|---|---|
| `POST` | `/api/auth/login` | auth — 5 req/min per IP |
| `GET` | `/api/cheap/items` | cheap — 100 req/min per user, burst 150 |
| `GET` | `/api/expensive/report` | expensive — 5 req/min per user, no burst |
| `GET` | `/api/ping` | default — 60 req/min per user-or-IP |

All endpoints just echo `{"status":"ok"}` — they exist to demonstrate the limiter, not the business logic.

---

## Tests

```bash
./mvnw test
```

Test layout:
- `algorithm/*IT` — integration tests against a Testcontainers Redis. Cover limit enforcement, recovery after window, atomic behavior under 20 concurrent threads.
- `core/RuleMatcherTest` — unit, no Spring context. Pattern specificity ordering, default fallback.
- `key/CompositeKeyResolverTest` — unit. Forwarded-For parsing, user-vs-IP precedence.
- `web/RateLimitFilterIT` — full HTTP integration. End-to-end behavior: 429 status, problem+json body, independent buckets per user, actuator exemption.

---

## Design decisions

1. **Lua, not WATCH/MULTI/EXEC.** Optimistic locking with WATCH retries can starve under contention. Lua is a single atomic step regardless of contention.
2. **UUID as ZSET member, not timestamp.** Two requests at the same millisecond would collide on member uniqueness and the counter would undercount. UUID guarantees distinct members.
3. **Float tokens stored as Redis strings.** Tokens accrue fractionally between calls. Integer-scaled tokens (multiplied by 1000) would be marginally faster but obscure the algorithm.
4. **Fail-open by default.** A rate limiter outage cascading into 100% 503s is worse than briefly permitting traffic. Fail-closed is opt-in for security-critical use cases.
5. **AntPathMatcher with specificity sort.** `/api/auth/**` outranks `/api/**` even if the latter is declared first. Matches Spring MVC mapping behavior, so the rules-to-routes correspondence is what a Spring developer expects.

---

## What this would need before production

- Per-instance circuit breaker so we don't hammer Redis when it's down (Resilience4j around the Lua execution).
- IP allowlist for trusted proxies — current code blindly trusts the first `X-Forwarded-For` entry. In a real deployment with multiple proxy hops you'd take the rightmost untrusted IP.
- Authentication: `X-User-Id` is a stand-in. Real wiring would resolve user from `SecurityContextHolder` populated by JWT validation.
- Dynamic rule reloading (Spring Cloud Config / DB-backed rules) so ops can tighten a limit without redeploying.
- Distinct keyspace prefix per environment (`rl:prod:`, `rl:staging:`) to avoid cross-pollination when sharing a Redis cluster.
