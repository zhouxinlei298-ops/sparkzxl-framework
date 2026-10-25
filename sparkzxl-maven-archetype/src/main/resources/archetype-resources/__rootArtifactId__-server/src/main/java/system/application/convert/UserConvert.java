#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.system.application.convert;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import ${package}.system.application.model.UserQueryDTO;
import ${package}.system.application.model.UserSaveDTO;
import ${package}.system.application.model.UserUpdateDTO;
import ${package}.system.application.model.UserVO;
import ${package}.system.infrastructure.entity.User;

import java.util.List;

/**
 * <p>
 * 系统用户 转换类
 *
 * @author zhouxinlei
 */
@Mapper
public interface UserConvert {

    UserConvert INSTANCE = Mappers.getMapper(UserConvert.class);

    /**
     * userQueryDTO转换为User
     *
     * @param userQueryDTO 系统用户查询对象
     * @return User
     */
    User convertUser(UserQueryDTO userQueryDTO);

    /**
     * userSaveDTO转换为User
     *
     * @param userSaveDTO 系统用户保存对象
     * @return User
     */
    User convertUser(UserSaveDTO userSaveDTO);

    /**
     * userUpdateDTO转换为User
     *
     * @param userUpdateDTO 系统用户更新对象
     * @return User
     */
    User convertUser(UserUpdateDTO userUpdateDTO);

    /**
     * User转换为UserVO
     *
     * @param user 系统用户
     * @return UserVO
     */
    UserVO convertUserVo(User user);

    /**
     * user列表转换为UserVO列表
     *
     * @param userList 系统用户列表
     * @return List<UserVO>
     */
    List<UserVO> convertUserVoList(List<User> userList);

    /**
     * 系统用户分页对象转换为UserVO分页对象
     *
     * @param userPage 系统用户分页对象
     * @return Page<UserVO>
     */
    Page<UserVO> convertUserVoPage(Page<User> userPage);
}
