#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.system.application.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * <p>
 * 用户创建事件监听
 * <p>演示进程内事件的消费方式，实际业务可替换为审计落库、缓存刷新等。</p>
 *
 * @author zhouxinlei
 */
@Slf4j
@Component
public class UserCreatedListener {

    /**
     * 处理用户创建事件。
     *
     * @param event 用户创建事件
     */
    @EventListener
    public void onUserCreated(UserCreatedEvent event) {
        log.info("用户创建事件: userId={}, account={}, operator={}",
                event.getUserId(), event.getAccount(), event.getOperator());
    }
}
