#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.system.application.service;

import ${package}.system.application.convert.UserConvert;
import ${package}.system.application.event.UserCreatedEvent;
import ${package}.system.application.exception.UserAccountExistedException;
import ${package}.system.application.model.UserSaveDTO;
import ${package}.system.application.model.UserUpdateDTO;
import ${package}.system.application.port.CurrentUserProvider;
import ${package}.system.domain.model.Operator;
import ${package}.system.domain.repository.UserRepository;
import ${package}.system.infrastructure.entity.User;
import com.github.sparkzxl.core.util.ArgumentAssert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <p>
 * 系统用户应用服务实现（写模型）
 *
 * @author zhouxinlei
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    private final CurrentUserProvider currentUserProvider;

    private final ApplicationEventPublisher eventPublisher;

    /**
     * 新增系统用户：账号唯一性校验后落库，并发布进程内创建事件。
     *
     * @param userSaveDTO 系统用户新增DTO
     * @return boolean
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(UserSaveDTO userSaveDTO) {
        User existed = userRepository.getByAccount(userSaveDTO.getAccount());
        if (existed != null) {
            throw new UserAccountExistedException(userSaveDTO.getAccount());
        }
        User user = UserConvert.INSTANCE.convertUser(userSaveDTO);
        boolean result = userRepository.save(user);
        Operator operator = currentUserProvider.currentUser();
        eventPublisher.publishEvent(new UserCreatedEvent(user.getId(), user.getAccount(), operator.getName()));
        return result;
    }

    /**
     * 修改系统用户。
     *
     * @param userUpdateDTO 系统用户修改DTO
     * @return boolean
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(UserUpdateDTO userUpdateDTO) {
        ArgumentAssert.notNull(userUpdateDTO.getId(), "ID不能为空");
        User user = UserConvert.INSTANCE.convertUser(userUpdateDTO);
        return userRepository.updateById(user);
    }

    /**
     * 根据id删除系统用户。
     *
     * @param id id
     * @return boolean
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteById(Long id) {
        return userRepository.removeById(id);
    }
}
