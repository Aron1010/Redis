## Jedis

**1. Jedis**
**导入依赖**
```
<dependency>
    <groupId>redis.clients</groupId>
    <artifactId>jedis</artifactId>
    <version>3.7.0</version>
</dependency>
```
**Java完整示例**
```
Jedis jedis = new Jedis("10.211.55.3", 6379);
jedis.auth("123123");
jedis.select(0);

String result = jedis.set("name", "Aron");
System.out.println("result: " + result);

String name = jedis.get("name");
System.out.println("name: " + name);

jedis.close();
```

**2. JUnit测试写法**
**导入依赖**
```
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter-engine</artifactId>
    <version>5.7.0</version>
    <scope>test</scope>
</dependency>
```
**测试**
```
private Jedis jedis;

@BeforeEach
void setUp() {
    jedis = new Jedis("10.211.55.3", 6379);
    jedis.auth("123123");
    jedis.select(0);
}

@Test
void testString() {
    String result = jedis.set("name", "Aron");
    System.out.println("result:" + result);

    String name = jedis.get("name");
    System.out.println("name:" + name);
}

@AfterEach
void tearDown() {
    if (jedis != null) {
        jedis.close();
    }
}
```

**3. jedis地址池**
| 对比项 | 普通 Jedis 连接 | JedisPool 连接池 |
|---|---|---|
| 创建方式 | 每次 new Jedis | 从连接池获取 |
| 性能 | 较低 | 较高 |
| 资源利用 | 连接频繁创建销毁 | 连接复用 |
| 适用场景 | 简单测试 | 实际开发 |
| 关闭方式 | 真正关闭连接 | 归还连接池 |

**配置地址池**
```
private static JedisPool jedisPool;

static {
    //配置连接池
    JedisPoolConfig config = new JedisPoolConfig();
    config.setMaxTotal(10);//最大连接
    config.setMaxIdle(10);//最大空闲连接
    config.setMinIdle(0);//最小空闲连接
    config.setMaxWaitMillis(5000);//设置等待时间，ms

    //创建连接池对象
    jedisPool = new JedisPool(config, "10.221.55.3", 6379,1000,"123123");
}

//获取jedis对象
public static Jedis getJedis() {
    return jedisPool.getResource();
}

//建立连接
jedis = JedisConnectionFactory.getJedis();
```