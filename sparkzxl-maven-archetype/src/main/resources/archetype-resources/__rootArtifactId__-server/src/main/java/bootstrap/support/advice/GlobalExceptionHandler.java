#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.bootstrap.support.advice;

import com.github.sparkzxl.core.base.result.R;
import com.github.sparkzxl.core.support.ExceptionCodeResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ${package}.system.application.exception.UserAccountExistedException;

/**
 * description: 项目级全局异常处理器
 * <p>框架已通过 sparkzxl-web-starter 的 DefaultExceptionHandler 覆盖业务异常、
 * 参数校验、servlet 等通用异常；本 advice 只承接项目自定义异常并映射为稳定错误码，
 * 避免与框架处理器产生歧义。若存在协议端点（如 OAuth），请参考
 * authorization-server 的做法为协议面单独建 advice 并用 @Order + assignableTypes 限定。</p>
 *
 * @author zhouxinlei
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler implements Ordered {

    /**
     * 处理用户账号重复异常。
     *
     * @param e 用户账号重复异常
     * @return 携带稳定错误码的统一失败响应
     */
    @ExceptionHandler(UserAccountExistedException.class)
    public R<?> handleUserAccountExisted(UserAccountExistedException e) {
        log.warn("用户账号重复: code={}", e.getErrorCode());
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
