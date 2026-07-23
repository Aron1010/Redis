package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

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

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 判断是否需要根据坐标查询
        if(x == null || y == null) {
            // 根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }
        // 计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        // 查询redis，按照距离排序，分页。结果：shopId，distance
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        // 解析出id
        if(results == null) {
            return Result.ok();
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if(list.size() <= from) {
            return Result.ok();
        }
        // 截取from-end的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String,Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            // 获取店铺id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            // 获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        // 根据id查询shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        // 返回
        return Result.ok(shops);

    }
}
