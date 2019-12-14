package org.redis.limit.util;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;
import redis.clients.jedis.Jedis;

import java.io.FileNotFoundException;
import java.util.List;
@Service
@Slf4j
public class RedisLimiter {

    private Jedis jedis;
    public String script;
    //引入redis执行lua的脚本支持

    public void init(Jedis jedis){
        this.jedis = jedis;
//        getRedisScript= new DefaultRedisScript<>();
//        //设置Lua脚本的返回值类型Long
//        getRedisScript.setResultType(Long.class);
//        //加载Lua脚本
//        getRedisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("rateLimite.lua")));
        script =  "-- 令牌桶限流: 不支持预消费, 初始桶是满的\n" +
                "-- KEYS[1]  string  限流的key\n" +
                "\n" +
                "-- ARGV[1]  int     桶最大容量\n" +
                "-- ARGV[2]  int     每次添加令牌数\n" +
                "-- ARGV[3]  int     令牌添加间隔(秒)\n" +
                "-- ARGV[4]  int     当前时间戳\n" +
                "\n" +
                "local bucket_capacity = tonumber(ARGV[1])\n" +
                "local add_token = tonumber(ARGV[2])\n" +
                "local add_interval = tonumber(ARGV[3])\n" +
                "local now = tonumber(ARGV[4])\n" +
                "\n" +
                "-- 保存上一次更新桶的时间的key\n" +
                "local LAST_TIME_KEY = KEYS[1]..\"_time\";         \n" +
                "-- 获取当前桶中令牌数\n" +
                "local token_cnt = redis.call(\"get\", KEYS[1])    \n" +
                "-- 桶完全恢复需要的最大时长\n" +
                "local reset_time = math.ceil(bucket_capacity / add_token) * add_interval;\n" +
                "\n" +
                "if token_cnt then   -- 令牌桶存在\n" +
                "    -- 上一次更新桶的时间\n" +
                "    local last_time = redis.call('get', LAST_TIME_KEY)\n" +
                "    -- 恢复倍数\n" +
                "    local multiple = math.floor((now - last_time) / add_interval)\n" +
                "    -- 恢复令牌数\n" +
                "    local recovery_cnt = multiple * add_token\n" +
                "    -- 确保不超过桶容量\n" +
                "    local token_cnt = math.min(bucket_capacity, token_cnt + recovery_cnt) - 1\n" +
                "    \n" +
                "    if token_cnt < 0 then\n" +
                "        return -1;\n" +
                "    end\n" +
                "    \n" +
                "    -- 重新设置过期时间, 避免key过期\n" +
                "    redis.call('set', KEYS[1], token_cnt, 'EX', reset_time)                     \n" +
                "    redis.call('set', LAST_TIME_KEY, last_time + multiple * add_interval, 'EX', reset_time)\n" +
                "    return token_cnt\n" +
                "    \n" +
                "else    -- 令牌桶不存在\n" +
                "    token_cnt = bucket_capacity - 1\n" +
                "    -- 设置过期时间避免key一直存在\n" +
                "    redis.call('set', KEYS[1], token_cnt, 'EX', reset_time);\n" +
                "    redis.call('set', LAST_TIME_KEY, now, 'EX', reset_time + 1);    \n" +
                "    return token_cnt    \n" +
                "end";

    }
    /**
     * 申请令牌
     * @return
     */
    public boolean applyToken(String service){
        //service是key,下一个数组代表  桶最大容量,每次添加令牌数,令牌添加间隔(秒),当前时间戳
        Object result = jedis.eval(script, Lists.newArrayList(service),
                Lists.newArrayList("11","1","1", System.currentTimeMillis()/1000 + ""));
        boolean flag= true;
        if (result.toString().equals("-1"))
        {
            flag = false;
        }else {
            System.out.println("令牌数量:"+result.toString());

        }
        return flag;
    }
    /**
     * 清理token
     * @throws FileNotFoundException
     * @throws InterruptedException
     */
    public void clear(String service){
        jedis.del(service+":tokens");
        jedis.del(service+":timestamp");
    }


}
