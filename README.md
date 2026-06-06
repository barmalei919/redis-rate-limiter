# redis-rate-limiter

HTTP rate limiter на Spring Boot + Redis. Пет-проект, не библиотека.

Что внутри:
- два алгоритма (sliding window и token bucket), оба через атомарные Lua-скрипты
- правила привязаны к route-паттернам, у каждого свой алгоритм и identity
- identity берется из заголовка `X-User-Id` или из IP (или композитно)
- метрики Micrometer в Prometheus, дашборд в Grafana
- если Redis недоступен - fail-open + счетчик ошибок

## Зачем два алгоритма

Эндпоинты разные. Логин надо резать жестко: 6 запросов в минуту - это уже brute force, никаких бурстов. А обычное API стоит делать терпимым к коротким всплескам (юзер быстро кликнул), и резать только когда средний rate реально превышен.

- Sliding window держит точный лимит в окне, бурстов не бывает. Подходит для auth, password reset, SMS - всего где "5 за минуту" уже атака.
- Token bucket копит токены до `burstCapacity`, тратит по одному на запрос, refill идет со скоростью `limit / windowMs`. Можно отдать 150 разом, но в среднем больше 100/мин не получится.

В проекте `/api/auth/**` сидит на sliding window, остальное API на token bucket.

## Атомарность

Наивная схема `count = INCR(key); if count > limit reject` ломается под нагрузкой: два клиента читают `count=9`, оба думают "еще можно", оба инкрементят. Лимит превышен. Классический TOCTOU.

Поэтому вся логика лежит в Lua-скрипте, который Redis исполняет атомарно - между шагами никто не вклинится. Скрипты в `src/main/resources/scripts/`, короткие, читаются за минуту.

## Архитектура

```
HTTP request
   |
   v
RateLimitFilter           (пропускает /actuator/**)
   |
   v
RateLimitService          (facade)
   |
   +-- RuleMatcher        (path -> Rule, выигрывает самый специфичный паттерн)
   +-- KeyResolverRegistry (Rule.identity -> IP / User / Composite)
   +-- RateLimiterRegistry (Rule.algorithm -> SlidingWindow / TokenBucket)
   +-- RateLimitMetrics    (allowed/denied/redis_failures счетчики)
   |
   v
Decision -> X-RateLimit-Limit, X-Remaining, X-Rule в хедерах
   |
   v  если denied
429 + Retry-After + application/problem+json
```

В Redis:
- Sliding window: `ZSET` на пару (правило, identity). Каждый запрос это элемент со score = timestamp и member = UUID. На каждом вызове чистим протухшие через `ZREMRANGEBYSCORE`, считаем, добавляем.
- Token bucket: `HASH` с двумя полями: `t` (текущее количество токенов, дробное) и `ts` (когда был последний пересчет).

Оба ключа получают `PEXPIRE` на каждом вызове, чтобы простаивающие identity сами протухали и не копили мусор.

## Конфигурация

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

Identity:
- `IP` - первый IP из `X-Forwarded-For` или `RemoteAddr`
- `USER` - заголовок `X-User-Id`, если пусто - падаем в IP
- `COMPOSITE` - user если есть, иначе IP

`fail-open: true` - если Redis упал, запросы идут дальше, но в `ratelimit_redis_failures_total` капают ошибки. Логика: лучше пропустить пару секунд лишнего трафика, чем положить весь сервис из-за моргнувшего лимитера. Для auth в проде это решение надо передумать.

## Запуск

Только Redis:

```
docker compose up -d redis
./mvnw spring-boot:run
curl -i -X POST http://localhost:8080/api/auth/login
```

Шестой запрос за минуту вернет:

```
HTTP/1.1 429 Too Many Requests
Retry-After: 47
X-RateLimit-Rule: auth
X-RateLimit-Limit: 5
X-RateLimit-Remaining: 0
Content-Type: application/problem+json
```

Полный стек с метриками:

```
docker compose up -d
./mvnw spring-boot:run
```

- App: http://localhost:8080
- Метрики: http://localhost:8080/actuator/prometheus
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (anonymous viewer или admin/admin)

Дашборд "Rate Limiter" появится в Grafana сам, провижится из `grafana/provisioning/`.

Нагрузочный тест, нужен [k6](https://k6.io/):

```
k6 run k6/load-test.js
```

Минута трех параллельных профилей трафика (cheap, expensive, auth). В Grafana во время прогона видно как линия denied для каждого правила выходит ровно на свой лимит.

## Эндпоинты

| Method | Path | Правило |
|---|---|---|
| `POST` | `/api/auth/login` | 5/мин по IP, sliding window |
| `GET` | `/api/cheap/items` | 100/мин по user или IP, token bucket, burst 150 |
| `GET` | `/api/expensive/report` | 5/мин по user, token bucket, без бурста |
| `GET` | `/api/ping` | default, 60/мин |

Внутри везде `{"status":"ok"}`, бизнес-логики нет, эндпоинты нужны только чтобы было что лимитировать.

## Тесты

```
./mvnw test
```

- `algorithm/SlidingWindowLimiterIT`, `TokenBucketLimiterIT` - интеграционные, Redis поднимается через Testcontainers. Проверяют лимит, retryAfter, восстановление после окна, поведение под параллельной нагрузкой (20 потоков по 10 запросов).
- `core/RuleMatcherTest` - юнит. Specificity-сортировка паттернов, fallback на default.
- `key/CompositeKeyResolverTest` - юнит. Парсинг `X-Forwarded-For`, приоритет user над IP.
- `web/RateLimitFilterIT` - end-to-end через `TestRestTemplate`. Статус 429, тело `application/problem+json`, изоляция по userId, освобождение `/actuator/**` от лимитов.

