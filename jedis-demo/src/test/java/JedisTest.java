import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

public class JedisTest {
    private Jedis jedis;


    @BeforeEach
    void setUp(){
        jedis = new Jedis("10.211.55.3",6379,9000);//建立连接
        jedis.auth("123123");//设置密码
        jedis.select(0);//选择库
    }

    @Test
    void testString(){
        String result = jedis.set("name", "Aron");//存入数据
        System.out.println("result:"+result);
        String name = jedis.get("name");//获取数据
        System.out.println("name:"+name);
    }
    @AfterEach
    void tearDown(){
        if(jedis != null){
            jedis.close();
        }
    }
}
