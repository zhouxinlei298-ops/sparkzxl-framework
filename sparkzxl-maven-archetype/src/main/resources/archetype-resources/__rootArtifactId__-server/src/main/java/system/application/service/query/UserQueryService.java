#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.system.application.service.query;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import ${package}.system.application.model.UserQueryDTO;
import ${package}.system.application.model.UserVO;
import ${package}.system.infrastructure.entity.User;

import java.util.List;

/**
 * <p>
 * 系统用户查询服务（读模型）
 * <p>轻量 CQRS：读操作与写服务分离，只做查询组装与模型转换，不开启写事务。</p>
 *
 * @author zhouxinlei
 */
public interface UserQueryService {

    /**
     * 系统用户分页条件查询
     *
     * @param page        分页入参
     * @param userQueryDTO 系统用户查询对象
     * @return Page<UserVO>
     */
    Page<UserVO> page(Page<User> page, UserQueryDTO userQueryDTO);

    /**
     * 系统用户集合条件查询
     *
     * @param userQueryDTO 系统用户查询对象
     * @return List<UserVO>
     */
    List<UserVO> list(UserQueryDTO userQueryDTO);

    /**
     * 根据id查询系统用户（优先走缓存）
     *
     * @param id id
     * @return UserVO
     */
    UserVO getById(Long id);
}
