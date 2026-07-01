package com.aron;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    /**
     * 自定义 RedisTemplate
     *
     * RedisTemplate 默认使用 JDK 序列化方式，
     * 存到 Redis 里的数据会出现乱码，不方便查看。
     *
     * 所以这里手动设置序列化方式：
     * key 使用 StringRedisSerializer
     * value 使用 Jackson2JsonRedisSerializer
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {

        // 1. 创建 RedisTemplate 对象
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();

        // 2. 设置 Redis 连接工厂
        // 连接工厂由 Spring Boot 根据 application.yml 自动创建
        redisTemplate.setConnectionFactory(connectionFactory);

        // 3. 创建 String 序列化器
        // 用来序列化 key，例如 name、user:1、shop:1
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();

        // 4. 创建 JSON 序列化器
        // 用来序列化 value，把 Java 对象转成 JSON 字符串存入 Redis
        Jackson2JsonRedisSerializer<Object> jsonRedisSerializer =
                new Jackson2JsonRedisSerializer<>(Object.class);

        // 5. 创建 ObjectMapper 对象
        // ObjectMapper 是 Jackson 提供的 JSON 转换工具
        ObjectMapper objectMapper = new ObjectMapper();

        // 6. 设置对象所有属性都可以被序列化
        // 包括 private 修饰的字段
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);

        // 7. 启用类型信息
        // 这样反序列化时，Redis 可以知道原来的 Java 对象类型
        objectMapper.activateDefaultTyping(
                objectMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL
        );

        // 8. 把 ObjectMapper 设置给 JSON 序列化器
        jsonRedisSerializer.setObjectMapper(objectMapper);

        // 9. 设置 key 的序列化方式
        // Redis 中普通 key 使用字符串序列化
        redisTemplate.setKeySerializer(stringRedisSerializer);

        // 10. 设置 value 的序列化方式
        // Redis 中普通 value 使用 JSON 序列化
        redisTemplate.setValueSerializer(jsonRedisSerializer);

        // 11. 设置 Hash 类型 key 的序列化方式
        // 例如 hset user:1 name Aron 中的 user:1
        redisTemplate.setHashKeySerializer(stringRedisSerializer);

        // 12. 设置 Hash 类型 value 的序列化方式
        // 例如 hset user:1 name Aron 中的 Aron
        redisTemplate.setHashValueSerializer(jsonRedisSerializer);

        // 13. 初始化 RedisTemplate
        redisTemplate.afterPropertiesSet();

        // 14. 返回配置好的 RedisTemplate
        return redisTemplate;
    }
}