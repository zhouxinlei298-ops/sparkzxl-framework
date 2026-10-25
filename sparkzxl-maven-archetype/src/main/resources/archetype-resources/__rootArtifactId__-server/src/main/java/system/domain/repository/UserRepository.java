#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.system.domain.repository;

import com.github.sparkzxl.mybatis.base.service.SuperCacheService;
import ${package}.system.infrastructure.entity.User;

/**
 * <p>
 * 系统用户 仓储类
 * <p>领域仓储接口：方法签名表达业务语义（如按账号取用户），
 * 继承框架 SuperCacheService 获得主键缓存能力；
 * 实现位于 infrastructure/repository 下。</p>
 *
 * @author zhouxinlei
 */
public interface UserRepository extends SuperCacheService<User> {

    /**
     * 根据账号查询用户。
     *
     * @param account 账号
     * @return 用户；不存在时返回 null
     */
    User getByAccount(String account);
}
