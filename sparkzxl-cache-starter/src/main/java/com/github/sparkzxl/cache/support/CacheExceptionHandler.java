package com.github.sparkzxl.cache.support;

import com.github.sparkzxl.core.base.result.R;
import com.github.sparkzxl.core.constant.enums.BeanOrderEnum;
import com.github.sparkzxl.core.support.ExceptionCodeResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.data.redis.ClusterRedirectException;
import org.springframework.data.redis.ClusterStateFailureException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.TooManyClusterRedirectionsException;
import org.springframework.data.redis.connection.ClusterCommandExecutionFailureException;
import org.springframework.data.redis.connection.RedisSubscribedConnectionException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import redis.clients.jedis.exceptions.JedisException;

/**
 * description: 缓存异常处理
 *
 * @author zhouxinlei
 */
@ControllerAdvice
@RestController
@Slf4j
public class CacheExceptionHandler implements Ordered {

    @ExceptionHandler(ClusterRedirectException.class)
    public R<?> handleClusterRedirectException(ClusterRedirectException e) {
        log.error("ClusterRedirectException 异常:", e);
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler(JedisException.class)
    public R<?> handleJedisException(JedisException e) {
        log.error("JedisException 异常:", e);
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler(ClusterStateFailureException.class)
    public R<?> handleClusterStateFailureException(ClusterStateFailureException e) {
        log.error("ClusterStateFailureException 异常:", e);
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler(RedisConnectionFailureException.class)
    public R<?> handleRedisConnectionFailureException(RedisConnectionFailureException e) {
        log.error("RedisConnectionFailureException 异常:", e);
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler(RedisSystemException.class)
    public R<?> handleRedisSystemException(RedisSystemException e) {
        log.error("RedisSystemException 异常:", e);
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler(TooManyClusterRedirectionsException.class)
    public R<?> handleTooManyClusterRedirectionsException(TooManyClusterRedirectionsException e) {
        log.error("TooManyClusterRedirectionsException 异常:", e);
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler(ClusterCommandExecutionFailureException.class)
    public R<?> handleClusterCommandExecutionFailureException(ClusterCommandExecutionFailureException e) {
        log.error("ClusterCommandExecutionFailureException 异常:", e);
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler(RedisSubscribedConnectionException.class)
    public R<?> handleRedisSubscribedConnectionException(RedisSubscribedConnectionException e) {
        log.error("RedisSubscribedConnectionException 异常:", e);
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @Override
    public int getOrder() {
        return BeanOrderEnum.CACHE_EXCEPTION_ORDER.getOrder();
    }
}
