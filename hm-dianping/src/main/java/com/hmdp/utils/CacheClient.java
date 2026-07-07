package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import com.sun.xml.internal.bind.v2.model.core.ID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
@Slf4j
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit) {
        // 设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(time));
        // 写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value));
    }
    public <R,ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> deFallback, Long time, TimeUnit timeUnit) {
        String key = keyPrefix+id;
        // 1.从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if(StrUtil.isNotEmpty(shopJson)) {
            // 3.存在，直接返回
            return JSONUtil.toBean(shopJson, type);
        }

        // 4.判断命中的是否是空值
        if(shopJson != null) {
            // 返回错误信息
            return null;
        }
        // 5.不存在，根据id查询数据库
        R r = deFallback.apply(id);
        // 6.不存在，返回错误
        if(r == null) {
            // 将空值写入redis
            stringRedisTemplate.opsForValue().set(key,"",2L, TimeUnit.MINUTES);
            return null;
        }
        // 7.存在，写入redis
        this.set(key, r, time, timeUnit);
        // 8.返回
        return r;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public <R,ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> deFallback, Long time, TimeUnit timeUnit) {
        String key = keyPrefix+id;
        // 1.从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 2.判断是否存在
        if(StrUtil.isEmpty(shopJson)) {
            // 3.存在，直接返回
            return null;
        }

        // 4.命中，需要把Json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(),type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5.判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())) {
            // 5.1未过期，直接返回店铺信息
            return r;
        }
        // 5.2已过期，需要缓存重建
        // 6.缓存重建
        // 6.1获取互斥锁
        String lockKey = "lock:shop:"+id;
        boolean isLock = tryLock(lockKey);

        // 6.2判断是否获取锁成功
        if(isLock) {
            // 6.3成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.execute(() -> {
                try {
                    // 查询数据库
                    R r1 = deFallback.apply(id);
                    //写入redis
                    this.setWithLogicalExpire(key, r1, time, timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unLock(lockKey);

                }
            });
        }
        // 6.4返回过期的店铺信息
        return r;
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }
}


