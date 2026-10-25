#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.system.application.exception;

import com.github.sparkzxl.core.support.code.IErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * <p>
 * 系统用户错误码
 * <p>对外暴露稳定错误码，提示文案可以调整，错误码不可变更。</p>
 *
 * @author zhouxinlei
 */
@Getter
@RequiredArgsConstructor
public enum UserErrorCodeEnum implements IErrorCode {

    /**
     * 账号已存在
     */
    ACCOUNT_EXISTED("user.account.existed", "账号已存在"),

    /**
     * 用户不存在
     */
    USER_NOT_FOUND("user.not.found", "用户不存在");

    private final String errorCode;

    private final String errorMsg;

}
