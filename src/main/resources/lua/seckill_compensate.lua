local stockKey = KEYS[1]
local orderKey = KEYS[2]
local pendingKey = KEYS[3]

local userId = ARGV[1]
local reason = ARGV[2]
local pendingTtlSeconds = tonumber(ARGV[3])

if (redis.call('sismember', orderKey, userId) == 1) then
    redis.call('incrby', stockKey, 1)
    redis.call('srem', orderKey, userId)
end

redis.call('hset', pendingKey, 'status', 'FAILED', 'reason', reason)
redis.call('expire', pendingKey, pendingTtlSeconds)

return 1
