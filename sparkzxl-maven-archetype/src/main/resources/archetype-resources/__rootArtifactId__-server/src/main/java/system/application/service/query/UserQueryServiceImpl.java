#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.system.application.service.query;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import ${package}.system.application.convert.UserConvert;
import ${package}.system.application.model.UserQueryDTO;
import ${package}.system.application.model.UserVO;
import ${package}.system.domain.repository.UserRepository;
import ${package}.system.infrastructure.entity.User;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 系统用户查询服务实现（读模型）
 *
 * @author zhouxinlei
 */
@Service
@RequiredArgsConstructor
public class UserQueryServiceImpl implements UserQueryService {

    private final UserRepository userRepository;

    /**
     * 分页条件查询。
     *
     * @param page         分页入参
     * @param userQueryDTO 系统用户查询对象
     * @return Page<UserVO>
     */
    @Override
    public Page<UserVO> page(Page<User> page, UserQueryDTO userQueryDTO) {
        Page<User> userPage = userRepository.page(page, buildQueryWrapper(userQueryDTO));
        return UserConvert.INSTANCE.convertUserVoPage(userPage);
    }

    /**
     * 集合条件查询。
     *
     * @param userQueryDTO 系统用户查询对象
     * @return List<UserVO>
     */
    @Override
    public List<UserVO> list(UserQueryDTO userQueryDTO) {
        List<User> userList = userRepository.list(buildQueryWrapper(userQueryDTO));
        return UserConvert.INSTANCE.convertUserVoList(userList);
    }

    /**
     * 根据id查询系统用户，优先读取缓存。
     *
     * @param id id
     * @return UserVO
     */
    @Override
    public UserVO getById(Long id) {
        User user = userRepository.getByIdCache(id);
        return UserConvert.INSTANCE.convertUserVo(user);
    }

    /**
     * 构建查询条件。
     *
     * @param userQueryDTO 系统用户查询对象
     * @return LambdaQueryWrapper<User>
     */
    private LambdaQueryWrapper<User> buildQueryWrapper(UserQueryDTO userQueryDTO) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        if (userQueryDTO == null) {
            return queryWrapper;
        }
        if (StringUtils.isNotBlank(userQueryDTO.getAccount())) {
            queryWrapper.like(User::getAccount, userQueryDTO.getAccount());
        }
        if (StringUtils.isNotBlank(userQueryDTO.getName())) {
            queryWrapper.like(User::getName, userQueryDTO.getName());
        }
        if (userQueryDTO.getStatus() != null) {
            queryWrapper.eq(User::getStatus, userQueryDTO.getStatus());
        }
        queryWrapper.orderByDesc(User::getId);
        return queryWrapper;
    }
}
