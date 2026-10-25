#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.system.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.sparkzxl.core.entity.cache.CacheKeyBuilder;
import com.github.sparkzxl.mybatis.base.service.impl.SuperCacheServiceImpl;
import org.springframework.stereotype.Repository;
import ${package}.system.domain.repository.UserRepository;
import ${package}.system.infrastructure.cache.UserCacheKeyBuilder;
import ${package}.system.infrastructure.entity.User;
import ${package}.system.infrastructure.mapper.UserMapper;

/**
 * <p>
 * 系统用户 仓储实现类
 * <p>继承 SuperCacheServiceImpl：写操作后自动淘汰主键缓存；
 * 新增/修改的审计字段由框架 MetaDataHandler 填充。</p>
 *
 * @author zhouxinlei
 */
@Repository
public class UserRepositoryImpl extends SuperCacheServiceImpl<UserMapper, User> implements UserRepository {

    /**
     * 缓存 key 构建器。
     *
     * @return 缓存key构建器
     */
    @Override
    protected CacheKeyBuilder cacheKeyBuilder() {
        return new UserCacheKeyBuilder();
    }

    /**
     * 根据账号查询用户。
     *
     * @param account 账号
     * @return 用户；不存在时返回 null
     */
    @Override
    public User getByAccount(String account) {
        return getOne(new LambdaQueryWrapper<User>().eq(User::getAccount, account), false);
    }
}
