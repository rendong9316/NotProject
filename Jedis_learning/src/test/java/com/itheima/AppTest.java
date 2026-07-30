package com.itheima;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;


/**
 * Unit test for simple App.
 */
public class AppTest{
    private Jedis jedis;

    @BeforeEach
    //建立连接
    void setUp() {
        jedis = new Jedis("127.0.0.1",6379);
        jedis.auth("1234");
        jedis.select(0);
    }

    @Test
    //测试连接
    void testString() {
        String a = jedis.set("name","rdong");
        System.out.println(a);
        String name = jedis.get("name");
        System.out.println(name);
    }

    @AfterEach
    //释放连接
    void tearDown() {
        if(jedis!=null){
            jedis.close();
        }
    }
}
