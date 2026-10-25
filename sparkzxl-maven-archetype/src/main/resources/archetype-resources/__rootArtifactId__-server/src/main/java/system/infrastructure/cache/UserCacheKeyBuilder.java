#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.system.infrastructure.cache;

import com.github.sparkzxl.core.entity.cache.CacheKeyBuilder;
import ${package}.system.infrastructure.entity.User;

import java.time.Duration;

/**
 * <p>
 * 系统用户缓存 key 构建器
 * <p>供 {@code SuperCacheServiceImpl} 按主键缓存 {@link User}，
 * key 形如 {tenant}:user:{id}。</p>
 *
 * @author zhouxinlei
 */
public class UserCacheKeyBuilder implements CacheKeyBuilder {

    /**
     * 缓存前缀，按业务对象隔离命名空间。
     *
     * @return 缓存前缀
     */
    @Override
    public String getPrefix() {
        return "user";
    }

    /**
     * 缓存过期时间。
     *
     * @return 过期时间
     */
    @Override
    public Duration getExpire() {
        return Duration.ofMinutes(30);
    }
}
