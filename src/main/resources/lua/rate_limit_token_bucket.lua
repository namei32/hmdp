local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

local data = redis.call('HMGET', key, 'tokens', 'last_time')
local tokens = tonumber(data[1])
local last_time = tonumber(data[2])

if tokens == nil then
    tokens = capacity
    last_time = now
end

local delta = math.max(0, now - last_time)
local refill = delta * rate / 1000
local current_tokens = math.min(capacity, tokens + refill)

if current_tokens < requested then
    redis.call('HMSET', key, 'tokens', current_tokens, 'last_time', now)
    redis.call('EXPIRE', key, 3600)
    return 0
end

redis.call('HMSET', key, 'tokens', current_tokens - requested, 'last_time', now)
redis.call('EXPIRE', key, 3600)
return 1
