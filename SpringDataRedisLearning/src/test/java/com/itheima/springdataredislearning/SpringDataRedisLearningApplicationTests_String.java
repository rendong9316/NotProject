package com.itheima.springdataredislearning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itheima.springdataredislearning.pojo.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
class SpringDataRedisLearningApplicationTests_String {
    @Autowired
    //注入
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void testString() {
        stringRedisTemplate.opsForValue().set("name","rendong");
        Object result =stringRedisTemplate.opsForValue().get("name");
        System.out.println(result);
    }

    private static final ObjectMapper mapper = new ObjectMapper();
    @Test
    void testSaveUser() throws JsonProcessingException {
        User user = new User("张三",26);
        String json = mapper.writeValueAsString(user);//手动序列化
        stringRedisTemplate.opsForValue().set("user:1",json);
        String result_user = stringRedisTemplate.opsForValue().get("user:1");
        User user1 = mapper.readValue(result_user, User.class);//手动反序列化
        System.out.println(user1);
    }

}
