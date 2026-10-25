#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.system.infrastructure.mapper;

import com.github.sparkzxl.mybatis.base.mapper.SuperMapper;
import org.apache.ibatis.annotations.Mapper;
import ${package}.system.infrastructure.entity.User;

/**
 * <p>
 * 系统用户 Mapper 接口
 * <p>继承框架 SuperMapper 额外获得 updateAllById、insertBatchSomeColumn、
 * 物理删除等扩展方法。</p>
 *
 * @author zhouxinlei
 */
@Mapper
public interface UserMapper extends SuperMapper<User> {

}
