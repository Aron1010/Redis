## SpringDataRedis  
StringRedisTemplate 是 Spring Data Redis 里专门操作字符串类型数据的工具类，比 RedisTemplate 更适合初学和普通字符串操作 


| 作用             | 说明                        |
| -------------- | ------------------------- |
| 简化 Redis 操作    | 不用手动创建 Jedis 连接           |
| 整合 Spring Boot | 可以通过配置文件连接 Redis          |
| 支持多种数据类型       | String、Hash、List、Set、ZSet |
| 支持连接池          | 底层可使用 Lettuce 或 Jedis     |
| 支持对象存储         | 可以把 Java 对象存入 Redis       |
| 支持序列化          | 可以配置 key 和 value 的序列化方式   |

<br>

| Redis 数据类型       | Spring Data Redis 操作对象 |
| ---------------- | ---------------------- |
| String           | opsForValue()          |
| Hash             | opsForHash()           |
| List             | opsForList()           |
| Set              | opsForSet()            |
| SortedSet / ZSet | opsForZSet()           |
  
<br>

**示例**
```
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-pool2</artifactId>
</dependency>

```
  
```
spring:
  data:
    redis:
      host: 10.211.55.3
      port: 6379
      password: 123123
      database: 0
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0
          max-wait: 100ms

```

```
@Autowired
private RedisTemplate redisTemplate;

@Test
void RedisTest() {
    redisTemplate.opsForValue().set("name", "aron");
    Object name = redisTemplate.opsForValue().get("name");
    System.out.println(name);
}

```
  
**序列化方式**

1. RedisTemplate可以接收任意Object作为值写入Redis，只不过写入前会把Object序列化为字节形式，默认是采用JDK序列化,因此写入到redis中的数据是这样的:  \xac\xed\x00\x05t\x00\x04aron  
缺点：可读性差，内存占用较大  
  
2. RedisTemplate的两种序列化实践方案:  
* 方案一:  
   自定义RedisTemplate  
   修改RedisTemplate的序列化器为GenericJackson2JsonRedisSerializer  
* 方案二:  
    使用StringRedisTemplate  
    写入Redis时，手动把对象序列化为JSON  
    读取Redis时，手动把读取到的JSON反序列化为对象  
  
  
**方案一**

  
```
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>

```
  
```
//手动设置序列化方式
@Configuration
public class RedisConfig {

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

```
  
**方案二**

为了节省内存空间，我们并不会使用SON序列化器来处理value，而是统一使用String序列化器，要求只能存储String类型的key和value。当需要存储ava对象时，手动完成对象的序列化和反序列化。  
  
```
@SpringBootTest
public class RedisTest {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void testString() {
        stringRedisTemplate.opsForValue().set("name", "Aron");

        String name = stringRedisTemplate.opsForValue().get("name");

        System.out.println(name);
    }
}

```
  
反序列化  
```
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>1.18.32</version>
</dependency>

```
  
```
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private String name;
    private Integer age;
}

@Test
void testUser() throws Exception {
    User user = new User("Aron", 20);
    //手动序列化
    String json = mapper.writeValueAsString(user);
    //写入数据
    stringRedisTemplate.opsForValue().set("user:1", json);
    //获取数据
    String result = stringRedisTemplate.opsForValue().get("user:1");
    //手动反序列化
    User userResult = mapper.readValue(result, User.class);

    System.out.println(userResult);
}

```
