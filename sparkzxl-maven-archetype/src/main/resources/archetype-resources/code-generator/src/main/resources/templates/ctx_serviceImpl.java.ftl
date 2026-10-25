package ${package.ServiceImpl};

import ${package.Entity}.${entity};
import ${package.Mapper}.${table.mapperName};
import ${package.Service}.${table.serviceName};
import com.github.sparkzxl.core.entity.cache.CacheKeyBuilder;
import com.github.sparkzxl.mybatis.base.service.impl.SuperCacheServiceImpl;
import org.springframework.stereotype.Repository;

/**
 * <p>
 * ${table.comment!} 仓储实现类
 * <p>写操作后自动淘汰主键缓存；缓存前缀默认取表名，需要自定义过期时间时
 * 参考骨架中的 UserCacheKeyBuilder 建独立构建器。
 *
 * @author ${author}
 * @since ${date}
 */
@Repository
public class ${table.serviceImplName} extends SuperCacheServiceImpl<${table.mapperName}, ${entity}> implements ${table.serviceName} {

    /**
     * 缓存 key 构建器，前缀按表名隔离
     *
     * @return 缓存key构建器
     */
    @Override
    protected CacheKeyBuilder cacheKeyBuilder() {
        return () -> "${table.name}";
    }
}
