local key = keys[1]
local userId = ARGV[1]
local score = ARGV[2]
if (not key or key == '') or (not userId or userId == '') or (not score) then
      return -1
  end

local oldScore = redis.call('ZSCORE', key, userId)
if oldScore then
    redis.call('ZREM', key, userId)
    return 0
else
    redis.call('ZADD', key, score, userId)
    return 1
end