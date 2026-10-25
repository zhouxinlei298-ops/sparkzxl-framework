#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.system.application.service;

import ${package}.system.application.event.UserCreatedEvent;
import ${package}.system.application.exception.UserAccountExistedException;
import ${package}.system.application.model.UserSaveDTO;
import ${package}.system.application.port.CurrentUserProvider;
import ${package}.system.domain.model.Operator;
import ${package}.system.domain.repository.UserRepository;
import ${package}.system.infrastructure.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * <p>
 * 系统用户应用服务单元测试
 * <p>应用服务的测试范式：仓储与端口全部 mock，只验证编排逻辑
 * （业务规则、事务边界内的事件发布），不依赖数据库与 Spring 上下文。</p>
 *
 * @author zhouxinlei
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private UserServiceImpl userService;

    /**
     * 账号已存在时应抛出稳定错误码异常，且不落库。
     */
    @Test
    void saveShouldThrowWhenAccountExisted() {
        when(userRepository.getByAccount("admin")).thenReturn(new User().setAccount("admin"));

        UserSaveDTO userSaveDTO = new UserSaveDTO()
                .setAccount("admin")
                .setPassword("123456")
                .setName("管理员");

        assertThrows(UserAccountExistedException.class, () -> userService.save(userSaveDTO));
    }

    /**
     * 保存成功时应发布用户创建事件并返回 true。
     */
    @Test
    void saveShouldPublishEventWhenSuccess() {
        when(userRepository.getByAccount("admin")).thenReturn(null);
        when(userRepository.save(any(User.class))).thenReturn(true);
        when(currentUserProvider.currentUser()).thenReturn(Operator.of(1L, "管理员"));

        UserSaveDTO userSaveDTO = new UserSaveDTO()
                .setAccount("admin")
                .setPassword("123456")
                .setName("管理员");

        assertTrue(userService.save(userSaveDTO));
        verify(eventPublisher).publishEvent(any(UserCreatedEvent.class));
    }
}
