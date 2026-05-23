local voucherId = ARGV[1]
local userId = ARGV[2]
local orderId = ARGV[3]
local pendingTtlSeconds = tonumber(ARGV[4])

local stockKey = KEYS[1]
local orderKey = KEYS[2]
local pendingKey = KEYS[3]

local stock = redis.call('get', stockKey)
if (not stock or tonumber(stock) <= 0) then
    return 1
end

if (redis.call('sismember', orderKey, userId) == 1) then
    return 2
end

redis.call('incrby', stockKey, -1)
redis.call('sadd', orderKey, userId)
redis.call('hset', pendingKey,
        'orderId', orderId,
        'userId', userId,
        'voucherId', voucherId,
        'status', 'PENDING')
redis.call('expire', pendingKey, pendingTtlSeconds)

return 0
