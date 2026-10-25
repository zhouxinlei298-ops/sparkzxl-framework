#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.system.application.port;

import ${package}.system.domain.model.Operator;

/**
 * <p>
 * 当前操作人端口
 * <p>application 层通过端口读取请求上下文，不直接依赖 web 技术设施；
 * 实现类位于 infrastructure/adapter 下（六边形架构的出站适配器）。</p>
 *
 * @author zhouxinlei
 */
public interface CurrentUserProvider {

    /**
     * 获取当前操作人；上下文缺失时返回匿名操作人而非 null。
     *
     * @return 当前操作人
     */
    Operator currentUser();
}
