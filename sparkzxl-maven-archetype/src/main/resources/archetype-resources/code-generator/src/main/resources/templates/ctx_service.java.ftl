package ${package.Service};

import ${package.Entity}.${entity};
import com.github.sparkzxl.mybatis.base.service.SuperCacheService;

/**
 * <p>
 * ${table.comment!} 仓储类
 * <p>领域仓储接口，主键缓存能力由 SuperCacheService 提供。
 *
 * @author ${author}
 * @since ${date}
 */
public interface ${table.serviceName} extends SuperCacheService<${entity}> {


}
