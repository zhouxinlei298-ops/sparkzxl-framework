#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.system.application.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * <p>
 * 用户创建事件
 * <p>进程内领域事件：由应用服务在事务内发布，
 * 监听方只做非核心链路的旁路处理（审计、缓存清理、通知等），
 * 跨服务可靠事件请使用 outbox 表 + 任务派发。</p>
 *
 * @author zhouxinlei
 */
@Getter
@RequiredArgsConstructor
public class UserCreatedEvent {

    private final Long userId;

    private final String account;

    private final String operator;
}
