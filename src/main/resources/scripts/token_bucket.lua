local key = KEYS[1]
local now = tonumber(ARGV[1])
local capacity = tonumber(ARGV[2])
local refill_per_ms = tonumber(ARGV[3])
local cost = tonumber(ARGV[4])

local state = redis.call('HMGET', key, 't', 'ts')
local tokens = tonumber(state[1])
local ts = tonumber(state[2])

if tokens == nil then
    tokens = capacity
    ts = now
end

local elapsed = math.max(0, now - ts)
tokens = math.min(capacity, tokens + elapsed * refill_per_ms)

local allowed = 0
local retry = 0
if tokens >= cost then
    tokens = tokens - cost
    allowed = 1
else
    retry = math.ceil((cost - tokens) / refill_per_ms)
end

redis.call('HSET', key, 't', tokens, 'ts', now)
local ttl = math.ceil(capacity / refill_per_ms) + 1000
redis.call('PEXPIRE', key, ttl)

return {allowed, math.floor(tokens), retry}
