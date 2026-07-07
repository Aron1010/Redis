package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private  StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    public ShopServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Result queryById(Long id) {

        // 缓存穿透
        //Shop shop = queryWithPassThrough(id);
//        Shop shop = cacheClient.queryWithPassThrough(
//                RedisConstants.CACHE_SHOP_KEY,id, Shop.class,this::getById,RedisConstants.CACHE_SHOP_TTL,TimeUnit.MINUTES
//        );
        // 互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);

        // 逻辑过期解决缓存击穿
        //Shop shop = queryWithLogicalExpire(id);
        Shop shop = cacheClient.queryWithLogicalExpire(
                RedisConstants.CACHE_SHOP_KEY,id,Shop.class,this::getById,RedisConstants.CACHE_SHOP_TTL,TimeUnit.MINUTES
        );

        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        // 返回
        return Result.ok(shop);
    }

//    public Shop queryWithPassThrough(Long id) {
//        String key = "cache:shop"+id;
//        // 1.从redis查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        // 2.判断是否存在
//        if(StrUtil.isNotEmpty(shopJson)) {
//            // 3.存在，直接返回
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return shop;
//        }
//
//        // 4.判断命中的是否是空值
//        if(shopJson != null) {
//            // 返回错误信息
//            return null;
//        }
//        // 5.不存在，根据id查询数据库
//        Shop shop = getById(id);
//        // 6.不存在，返回错误
//        if(shop == null) {
//            // 将空值写入redis
//            stringRedisTemplate.opsForValue().set(key,"",2L, TimeUnit.MINUTES);
//            return null;
//        }
//        // 7.存在，写入redis
//        stringRedisTemplate.opsForValue().set(key ,JSONUtil.toJsonStr(shop),30L, TimeUnit.MINUTES);
//        // 8.返回
//        return shop;
//    }
//
//    public Shop queryWithMutex(Long id) {
//        String key = "cache:shop"+id;
//        // 1.从redis查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//
//        // 2.判断是否存在
//        if(StrUtil.isNotEmpty(shopJson)) {
//            // 3.存在，直接返回
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return shop;
//        }
//
//        // 4.判断命中的是否是空值
//        if(shopJson != null) {
//            // 返回错误信息
//            return null;
//        }
//        // 5实现缓存重建
//        // 5.1获取互斥锁
//        String lockKey = "lock:lock:"+id;
//        Shop shop = null;
//        try {
//            boolean isLock = tryLock(lockKey);
//
//            // 5.2判断是否获取成功
//            if(!isLock) {
//                // 5.3获取失败，休眠并重试
//                Thread.sleep(50);
//                return queryWithPassThrough(id);
//            }
//
//            // 5.4成功，根据id查询数据库
//            shop = getById(id);
//            // 6.不存在，返回错误
//            if(shop == null) {
//                // 将空值写入redis
//                stringRedisTemplate.opsForValue().set(key,"",2L, TimeUnit.MINUTES);
//                return null;
//            }
//            // 7.存在，写入redis
//            stringRedisTemplate.opsForValue().set(key ,JSONUtil.toJsonStr(shop),30L, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }finally {
//            // 8. 释放互斥锁
//            unLock(lockKey);
//        }
//        // 9.返回
//        return shop;
//    }


    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

//    public Shop queryWithLogicalExpire(Long id) {
//        String key = "cache:shop"+id;
//        // 1.从redis查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//
//        // 2.判断是否存在
//        if(StrUtil.isEmpty(shopJson)) {
//            // 3.存在，直接返回
//            return null;
//        }
//
//        // 4.命中，需要把Json反序列化为对象
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        JSONObject data = (JSONObject) redisData.getData();
//        Shop shop = JSONUtil.toBean(data, Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//        // 5.判断是否过期
//        if(expireTime.isAfter(LocalDateTime.now())) {
//            // 5.1未过期，直接返回店铺信息
//            return shop;
//        }
//        // 5.2已过期，需要缓存重建
//        // 6.缓存重建
//        // 6.1获取互斥锁
//        String lockKey = "lock:shop:"+id;
//        boolean isLock = tryLock(lockKey);
//
//        // 6.2判断是否获取锁成功
//        if(isLock) {
//            // 6.3成功，开启独立线程，实现缓存重建
//            CACHE_REBUILD_EXECUTOR.execute(() -> {
//                try {
//                    // 重建缓存
//                    this.saveShop2Redis(id,30L);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                } finally {
//                    // 释放锁
//                    unLock(lockKey);
//
//                }
//            });
//        }
//
//
//        // 6.4返回过期的店铺信息
//        return shop;
//    }

//    private boolean tryLock(String key) {
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(flag);
//    }
//
//    private void unLock(String key) {
//        stringRedisTemplate.delete(key);
//    }
//
//    public void saveShop2Redis(Long id,Long expireSeconds) {
//        // 1.查询店铺数据
//        Shop shop = getById(id);
//        // 2.封装逻辑过期时间
//        RedisData redisData = new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
//        // 3.写入redis
//        stringRedisTemplate.opsForValue().set("cache:shop"+id,JSONUtil.toJsonStr(redisData));
//    }

    @Override
    @Transactional()
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 1.更新数据库
        updateById(shop);
        // 2.删除缓存
        stringRedisTemplate.delete("cache:shop"+shop.getId());
        return Result.ok();
    }
}
