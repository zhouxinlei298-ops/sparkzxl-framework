#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.system.application.exception;

import com.github.sparkzxl.core.support.BaseUncheckedException;

/**
 * <p>
 * 用户账号重复异常
 * <p>继承 BaseUncheckedException 而非 BizException，
 * 避免被框架 DefaultExceptionHandler 以通用业务异常兜底处理，
 * 保证错误码由本项目 advice 精确映射。</p>
 *
 * @author zhouxinlei
 */
public class UserAccountExistedException extends BaseUncheckedException {

    private static final long serialVersionUID = 1L;

    public UserAccountExistedException(String account) {
        super(UserErrorCodeEnum.ACCOUNT_EXISTED.getErrorCode(),
                UserErrorCodeEnum.ACCOUNT_EXISTED.getErrorMsg() + ": " + account);
    }
}
