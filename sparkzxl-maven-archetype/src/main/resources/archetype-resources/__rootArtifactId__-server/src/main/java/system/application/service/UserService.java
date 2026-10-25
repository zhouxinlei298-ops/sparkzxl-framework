#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.system.application.service;

import ${package}.system.application.model.UserSaveDTO;
import ${package}.system.application.model.UserUpdateDTO;

/**
 * <p>
 * 系统用户应用服务（写模型）
 * <p>轻量 CQRS：写操作集中在此，查询走 application/service/query 下的查询服务；
 * 事务边界在应用服务方法上。</p>
 *
 * @author zhouxinlei
 */
public interface UserService {

    /**
     * 新增系统用户
     *
     * @param userSaveDTO 系统用户新增DTO
     * @return boolean
     */
    boolean save(UserSaveDTO userSaveDTO);

    /**
     * 修改系统用户
     *
     * @param userUpdateDTO 系统用户修改DTO
     * @return boolean
     */
    boolean update(UserUpdateDTO userUpdateDTO);

    /**
     * 根据id删除系统用户
     *
     * @param id id
     * @return boolean
     */
    boolean deleteById(Long id);
}
