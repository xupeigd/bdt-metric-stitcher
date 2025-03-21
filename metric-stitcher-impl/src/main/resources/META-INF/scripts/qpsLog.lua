redis.replicate_commands()
local opKey = KEYS[1]
local qpsVal = KEYS[2]
local qps = tonumber(ARGV[1])
local oginalQps = tonumber(redis.call("ZINCRBY", opKey, 0, qpsVal))
if oginalQps < qps then
    qps = tonumber(redis.call("ZADD", opKey, qps, qpsVal))
end
return { oginalQps, qps }