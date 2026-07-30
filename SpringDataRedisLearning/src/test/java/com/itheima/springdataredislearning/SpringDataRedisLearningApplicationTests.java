package com.itheima.springdataredislearning;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

@SpringBootTest
class SpringDataRedisLearningApplicationTests {
    @Autowired
    //注入
    private RedisTemplate redisTemplate;

    @Test
    void testString() {
        redisTemplate.opsForValue().set("name","rend");
        Object result =redisTemplate.opsForValue().get("name");
        System.out.println(result);
    }

}
