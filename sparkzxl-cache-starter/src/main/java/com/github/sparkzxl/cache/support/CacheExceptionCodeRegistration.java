package com.github.sparkzxl.cache.support;

import com.github.sparkzxl.core.support.code.ExceptionErrorCode;
import com.github.sparkzxl.core.support.ExceptionCodeResolver;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.ClusterRedirectException;
import org.springframework.data.redis.ClusterStateFailureException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.TooManyClusterRedirectionsException;
import org.springframework.data.redis.connection.ClusterCommandExecutionFailureException;
import org.springframework.data.redis.connection.RedisSubscribedConnectionException;
import org.springframework.stereotype.Component;
import redis.clients.jedis.exceptions.JedisException;

/**
 * 缓存异常码注册
 *
 * @author zhouxinlei
 */
@Component
public class CacheExceptionCodeRegistration implements InitializingBean {

    @Override
    public void afterPropertiesSet() {
        ExceptionCodeResolver.register(ClusterRedirectException.class, ExceptionErrorCode.CLUSTER_REDIRECT_EXCEPTION);
        ExceptionCodeResolver.register(ClusterStateFailureException.class, ExceptionErrorCode.CLUSTER_STATE_FAILURE_EXCEPTION);
        ExceptionCodeResolver.register(RedisConnectionFailureException.class, ExceptionErrorCode.REDIS_CONNECTION_FAILURE_EXCEPTION);
        ExceptionCodeResolver.register(RedisSystemException.class, ExceptionErrorCode.REDIS_SYSTEM_EXCEPTION);
        ExceptionCodeResolver.register(TooManyClusterRedirectionsException.class, ExceptionErrorCode.TOO_MANY_CLUSTER_REDIRECTIONS_EXCEPTION);
        ExceptionCodeResolver.register(ClusterCommandExecutionFailureException.class, ExceptionErrorCode.CLUSTER_COMMAND_EXECUTION_FAILURE_EXCEPTION);
        ExceptionCodeResolver.register(RedisSubscribedConnectionException.class, ExceptionErrorCode.REDIS_SUBSCRIBED_CONNECTION_EXCEPTION);
        ExceptionCodeResolver.registerResolver(JedisException.class, ex ->
                new ExceptionCodeResolver.ResolvedError(ExceptionErrorCode.REDIS_SYSTEM_EXCEPTION.getErrorCode(), ex.getMessage()));
    }
}
