-- 令牌桶限流: 不支持预消费, 初始桶是满的
-- KEYS[1]  string  限流的key

-- ARGV[1]  int     桶最大容量
-- ARGV[2]  int     每次添加令牌数
-- ARGV[3]  int     令牌添加间隔(秒)
-- ARGV[4]  int     当前时间戳

local bucket_capacity = tonumber(ARGV[1])
local add_token = tonumber(ARGV[2])
local add_interval = tonumber(ARGV[3])
local now = tonumber(ARGV[4])

-- 保存上一次更新桶的时间的key
local LAST_TIME_KEY = KEYS[1].."_time";
-- 获取当前桶中令牌数
local token_cnt = redis.call("get", KEYS[1])
-- 桶完全恢复需要的最大时长
local reset_time = math.ceil(bucket_capacity / add_token) * add_interval;

if token_cnt then   -- 令牌桶存在
	-- 上一次更新桶的时间
	local last_time = redis.call('get', LAST_TIME_KEY)
	-- 恢复倍数
	local multiple = math.floor((now - last_time) / add_interval)
	-- 恢复令牌数
	local recovery_cnt = multiple * add_token
	-- 确保不超过桶容量
	local token_cnt = math.min(bucket_capacity, token_cnt + recovery_cnt) - 1

	if token_cnt < 0 then
		return -1;
	end

	-- 重新设置过期时间, 避免key过期
	redis.call('set', KEYS[1], token_cnt, 'EX', reset_time)
	redis.call('set', LAST_TIME_KEY, last_time + multiple * add_interval, 'EX', reset_time)
	return token_cnt

else    -- 令牌桶不存在
	token_cnt = bucket_capacity - 1
	-- 设置过期时间避免key一直存在
	redis.call('set', KEYS[1], token_cnt, 'EX', reset_time);
	redis.call('set', LAST_TIME_KEY, now, 'EX', reset_time + 1);
	return token_cnt
end