#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.system.infrastructure.adapter;

import com.github.sparkzxl.core.constant.BaseContextConstants;
import com.github.sparkzxl.core.context.RequestLocalContextHolder;
import org.springframework.stereotype.Component;
import ${package}.system.application.port.CurrentUserProvider;
import ${package}.system.domain.model.Operator;

/**
 * <p>
 * 当前操作人适配器
 * <p>CurrentUserProvider 端口的出站适配器：从框架请求上下文中解析操作人，
 * 上下文缺失时返回匿名操作人（fail-closed，不返回 null）。</p>
 *
 * @author zhouxinlei
 */
@Component
public class CurrentUserProviderAdapter implements CurrentUserProvider {

    /**
     * 获取当前操作人。
     *
     * @return 当前操作人
     */
    @Override
    public Operator currentUser() {
        Long userId = RequestLocalContextHolder.get(BaseContextConstants.JWT_KEY_USER_ID, Long.class, null);
        String name = RequestLocalContextHolder.get(BaseContextConstants.JWT_KEY_NAME);
        return Operator.of(userId, name);
    }
}
