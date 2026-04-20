package com.github.sparkzxl.lock.spring.boot.autoconfigure;

import com.github.sparkzxl.lock.autoconfigure.LockAutoConfiguration;
import com.github.sparkzxl.lock.executor.RedissonLockExecutor;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * description: Redisson锁自动配置
 *
 * @author zhouxinlei
 * @since 2022-06-21 15:50:50
 */
@Configuration
@AutoConfigureBefore(LockAutoConfiguration.class)
public class RedissonLockAutoConfiguration {

    @Bean
    public RedissonLockExecutor redissonLockExecutor(RedissonClient redissonClient) {
        return new RedissonLockExecutor(redissonClient);
    }

}
