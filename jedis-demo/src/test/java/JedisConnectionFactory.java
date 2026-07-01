import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class JedisConnectionFactory {
    private static JedisPool jedisPool;
    static {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(10);
        config.setMaxIdle(10);
        config.setMinIdle(10);
        config.setMaxWaitMillis(5000);
        jedisPool = new JedisPool(config, "10.221.55.3", 6379,1000,"123123");
    }
    public static Jedis getJedis() {
        return jedisPool.getResource();
    }
}
