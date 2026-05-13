-- 获取参数
local voucherId = ARGV[1]
local userId = ARGV[2]

-- 拼接Key
local stockKey = KEYS[1]
local orderKey = KEYS[2]

local stock = redis.call('get', stockKey)
if (not stock or tonumber(stock) <= 0) then
    return 1
end

-- 判断用户是否已经下过单
if (redis.call('sismember', orderKey, userId) == 1) then
    return 2
end

-- 扣库存
redis.call('incrby', stockKey, -1)
-- 将userId存入set
redis.call('sadd', orderKey, userId)

return 0
