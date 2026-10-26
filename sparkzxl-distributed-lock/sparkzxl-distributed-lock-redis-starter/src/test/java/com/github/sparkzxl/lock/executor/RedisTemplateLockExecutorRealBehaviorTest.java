package com.github.sparkzxl.lock.executor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * description: RedisTemplate 分布式锁真连接行为测试
 * <p>
 * 锁脚本必须对真实 Redis（cluster 模式 redis-cluster-verify 容器）执行验证，
 * mock 无法暴露 Lua 运行期类型与下标错误。覆盖四条路径：首次加锁、重复加锁冲突、
 * 释放（含非持有者释放）、锁过期后重入。通过环境变量 IAM_REAL_REDIS_HOST（可选
 * IAM_REAL_REDIS_PORT、IAM_REAL_REDIS_PASSWORD）启用，未设置时自动跳过。
 *
 * @author zhouxinlei
 * @since 2026-10-26
 */
@EnabledIfEnvironmentVariable(named = "IAM_REAL_REDIS_HOST", matches = ".+")
class RedisTemplateLockExecutorRealBehaviorTest {

    private static final long LOCK_EXPIRE_MILLIS = 60_000L;

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate stringRedisTemplate;
    private static RedisTemplateLockExecutor lockExecutor;
    private static String keyPrefix;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(host(), port());
        String password = System.getenv("IAM_REAL_REDIS_PASSWORD");
        if (password != null && !password.trim().isEmpty()) {
            connectionFactory.setPassword(password.trim());
        }
        connectionFactory.afterPropertiesSet();
        stringRedisTemplate = new StringRedisTemplate(connectionFactory);
        lockExecutor = new RedisTemplateLockExecutor(stringRedisTemplate);
        // 一次性前缀隔离测试数据，不清理共享 Redis。
        keyPrefix = "it-lock-" + System.nanoTime() + ":";
    }

    @AfterAll
    static void shutdown() {
        connectionFactory.destroy();
    }

    @Test
    void acquireLockOnMissingKeySucceeds() {
        String key = keyPrefix + "first-acquire";

        String lockInstance = lockExecutor.acquire(key, "owner-1", LOCK_EXPIRE_MILLIS, 3000L);

        assertThat(lockInstance).isEqualTo("OK");
        assertThat(stringRedisTemplate.opsForValue().get(key)).isEqualTo("owner-1");
        Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
        assertThat(ttl).isPositive().isLessThanOrEqualTo(LOCK_EXPIRE_MILLIS);
    }

    @Test
    void acquireLockOnHeldKeyConflicts() {
        String key = keyPrefix + "conflict";
        assertThat(lockExecutor.acquire(key, "owner-1", LOCK_EXPIRE_MILLIS, 3000L)).isEqualTo("OK");

        String secondAttempt = lockExecutor.acquire(key, "owner-2", LOCK_EXPIRE_MILLIS, 3000L);

        assertThat(secondAttempt).isNull();
        assertThat(stringRedisTemplate.opsForValue().get(key)).isEqualTo("owner-1");
    }

    @Test
    void releaseDeletesLockOnlyForHolder() {
        String key = keyPrefix + "release";
        assertThat(lockExecutor.acquire(key, "owner-1", LOCK_EXPIRE_MILLIS, 3000L)).isEqualTo("OK");

        // 非持有者解锁必须被拒绝（含键已被他人持有的 CAS 未命中路径）。
        assertThat(lockExecutor.releaseLock(key, "not-the-owner", "OK")).isFalse();
        assertThat(stringRedisTemplate.opsForValue().get(key)).isEqualTo("owner-1");

        assertThat(lockExecutor.releaseLock(key, "owner-1", "OK")).isTrue();
        assertThat(stringRedisTemplate.hasKey(key)).isFalse();

        // 键已删除后再次释放：GET 返回 false 的缺键路径，必须返回失败而非报错。
        assertThat(lockExecutor.releaseLock(key, "owner-1", "OK")).isFalse();
    }

    @Test
    void reAcquireAfterExpirySucceedsAndOldOwnerCannotRelease() throws Exception {
        String key = keyPrefix + "expiry";
        assertThat(lockExecutor.acquire(key, "owner-1", 1500L, 3000L)).isEqualTo("OK");

        // 等待超过过期时间，锁自动失效后新持有者可重入。
        TimeUnit.MILLISECONDS.sleep(2200L);
        assertThat(stringRedisTemplate.hasKey(key)).isFalse();

        assertThat(lockExecutor.acquire(key, "owner-2", LOCK_EXPIRE_MILLIS, 3000L)).isEqualTo("OK");
        assertThat(stringRedisTemplate.opsForValue().get(key)).isEqualTo("owner-2");

        // 过期前的旧持有者不能再释放已被新持有者接管的锁。
        assertThat(lockExecutor.releaseLock(key, "owner-1", "OK")).isFalse();
        assertThat(stringRedisTemplate.opsForValue().get(key)).isEqualTo("owner-2");
    }

    private static String host() {
        return System.getenv("IAM_REAL_REDIS_HOST");
    }

    private static int port() {
        String port = System.getenv("IAM_REAL_REDIS_PORT");
        return port == null || port.trim().isEmpty() ? 6379 : Integer.parseInt(port.trim());
    }
}
