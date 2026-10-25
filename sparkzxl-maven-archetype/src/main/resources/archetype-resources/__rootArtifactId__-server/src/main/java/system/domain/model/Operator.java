#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.system.domain.model;

import lombok.Getter;

/**
 * <p>
 * 操作人值对象
 * <p>领域层的不可变值对象：不依赖任何框架与持久化设施，
 * 表达"当前操作人"这一领域概念。</p>
 *
 * @author zhouxinlei
 */
@Getter
public class Operator {

    private static final String ANONYMOUS_NAME = "anonymous";

    private final Long userId;

    private final String name;

    private Operator(Long userId, String name) {
        this.userId = userId;
        this.name = name;
    }

    /**
     * 构建操作人；name 为空时回落为匿名标识。
     *
     * @param userId 用户id
     * @param name   用户名称
     * @return 操作人
     */
    public static Operator of(Long userId, String name) {
        if (userId == null && (name == null || name.trim().isEmpty())) {
            return anonymous();
        }
        return new Operator(userId, name == null || name.trim().isEmpty() ? ANONYMOUS_NAME : name);
    }

    /**
     * 匿名操作人（上下文缺失时的 fail-closed 缺省值）。
     *
     * @return 匿名操作人
     */
    public static Operator anonymous() {
        return new Operator(null, ANONYMOUS_NAME);
    }

    /**
     * 是否已认证。
     *
     * @return 已认证返回 true
     */
    public boolean isAuthenticated() {
        return userId != null;
    }
}
