package com.github.sparkzxl.lock.executor;

import java.util.Collections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * description: 分布式锁原生RedisTemplate处理器
 * <p>
 * 脚本面向阿里云集群版 Proxy 模式（静态校验）：键访问仅允许 KEYS[字面量]；
 * 本执行器两个脚本均为单键，无需 hash tag；解锁脚本必须先拦截缺键
 * （GET 对不存在的键返回 false，禁止与字符串直接比较）。
 *
 * @author zhouxinlei
 * @since 2022-05-01 22:17:52
 */
@Slf4j
@RequiredArgsConstructor
public class RedisTemplateLockExecutor extends AbstractLockExecutor<String> {

    /**
     * 过期毫秒数必须由调用方以字符串文本传入（ARGV[2]），裸数字经模板序列化会报 not an integer。
     */
    private static final RedisScript<String> SCRIPT_LOCK = new DefaultRedisScript<>("return redis.call('set',KEYS[1]," +
            "ARGV[1],'NX','PX',ARGV[2])", String.class);
    private static final RedisScript<Long> SCRIPT_UNLOCK = new DefaultRedisScript<>(
            "local current=redis.call('get',KEYS[1]); "
                    + "if current==false then return 0 end; "
                    + "if current~=ARGV[1] then return 0 end; "
                    + "return redis.call('del',KEYS[1])", Long.class);
    private static final String LOCK_SUCCESS = "OK";
    private static final long UNLOCK_SUCCESS = 1L;

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public String acquire(String lockKey, String lockValue, long expire, long acquireTimeout) {
        String lock = stringRedisTemplate.execute(SCRIPT_LOCK,
                stringRedisTemplate.getStringSerializer(),
                stringRedisTemplate.getStringSerializer(),
                Collections.singletonList(lockKey),
                lockValue, String.valueOf(expire));
        final boolean locked = LOCK_SUCCESS.equals(lock);
        return obtainLockInstance(locked, lock);
    }

    @Override
    public boolean releaseLock(String key, String value, String lockInstance) {
        // Long 返回值不经序列化器解码，resultSerializer 传 null；
        // ARGV 仍走字符串序列化器保持锁值为原始字节。
        Long releaseResult = stringRedisTemplate.execute(SCRIPT_UNLOCK,
                stringRedisTemplate.getStringSerializer(),
                null,
                Collections.singletonList(key), value);
        return releaseResult != null && releaseResult == UNLOCK_SUCCESS;
    }
}
