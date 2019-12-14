package org.redis.limit.Controller;

import com.google.common.collect.Lists;
import org.redis.limit.util.RedisLimiter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import redis.clients.jedis.Jedis;

import javax.annotation.Resource;
import java.io.FileNotFoundException;

@RestController
public class LimitController {

@Resource
private RedisLimiter redisLimiter;

//    请求参数:http://localhost:8080/limiting?str=myservice1
    @RequestMapping("limiting")
    public  void main(@RequestParam String str) throws FileNotFoundException, InterruptedException {
        Jedis jedis = new Jedis("localhost", 6379);
        try {
            redisLimiter.init(jedis);
            //清理环境
            redisLimiter.clear(str);
//            String script = redisLimiter.script;
            for(int i=0;i<500;i++){
                if (redisLimiter.applyToken(str)){
                    System.out.println("获得令牌");
                }else {
                    System.out.println("没有获得令牌");
                    Thread.sleep(2000);
                }
            }
        } finally {
            jedis.close();
        }
    }
}
