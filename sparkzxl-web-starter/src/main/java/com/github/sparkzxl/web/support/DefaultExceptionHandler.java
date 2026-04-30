package com.github.sparkzxl.web.support;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.github.sparkzxl.core.base.HttpCode;
import com.github.sparkzxl.core.base.result.R;
import com.github.sparkzxl.core.constant.enums.BeanOrderEnum;
import com.github.sparkzxl.core.support.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.util.NestedServletException;

import javax.security.auth.login.AccountNotFoundException;
import javax.servlet.ServletException;
import javax.validation.ConstraintViolationException;
import javax.validation.ValidationException;
import java.net.UnknownHostException;

/**
 * description: 全局异常处理
 *
 * @author zhouxinlei
 */
@ControllerAdvice
@RestController
@Slf4j
public class DefaultExceptionHandler implements Ordered {

    @ExceptionHandler(BizException.class)
    public R<?> handleBizException(BizException e) {
        log.error("BizException 异常:", e);
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler(JsonParseException.class)
    public R<?> handleJwtParseException(JsonParseException e) {
        log.error("JwtParseException:", e);
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler(ArgumentException.class)
    public R<?> handleArgumentException(ArgumentException e) {
        log.warn("ArgumentException 异常:", e);
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler(NestedServletException.class)
    public R<?> handleNestedServletException(NestedServletException e) {
        log.error("NestedServletException 异常:", e);
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler(ServletException.class)
    public R<?> handleServletException(ServletException e) {
        log.warn("ServletException:", e);
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    /**
     * jsr 规范中的验证异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public R<?> handleConstraintViolationException(ConstraintViolationException ex) {
        log.warn("ConstraintViolationException:", ex);
        return R.failDetail(ExceptionCodeResolver.resolve(ex));
    }

    /**
     * jsr 规范中的验证异常
     */
    @ExceptionHandler(ValidationException.class)
    public R<?> handleValidationException(ValidationException ex) {
        log.warn("ValidationException:", ex);
        return R.failDetail(ExceptionCodeResolver.resolve(ex));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.warn("方法参数无效异常:", e);
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public R<?> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("IllegalArgumentException 异常:", e);
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler(IllegalStateException.class)
    public R<?> handleIllegalStateException(IllegalStateException e) {
        log.warn("IllegalStateException:", e);
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    /**
     * form非法参数验证
     *
     * @param e 异常
     * @return CommonResult<?>
     */
    @ExceptionHandler(BindException.class)
    public R<?> handleBindException(BindException e) {
        log.warn("form非法参数验证异常:{}", e.getMessage());
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler({AccountNotFoundException.class})
    public R<?> handleAccountNotFoundException(AccountNotFoundException e) {
        log.warn("AccountNotFoundException异常:{}", e.getMessage());
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public R<?> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.error("请求方法不支持异常:{}", e.getMessage());
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<?> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.error("HttpMessageNotReadableException 异常:", e);
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public R<?> handleNoHandlerFoundException(NoHandlerFoundException e) {
        log.error("NoHandlerFoundException 异常:{}", e.getMessage());
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public R<?> handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException e) {
        log.error("HttpMediaTypeNotSupportedException 异常:{}", e.getMessage());
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public R<?> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.error("MethodArgumentTypeMismatchException:", e);
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler(InvalidFormatException.class)
    public R<?> handleInvalidFormatException(InvalidFormatException e) {
        log.error("InvalidFormatException异常:", e);
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler(NullPointerException.class)
    public R<?> handleNullPointerException(NullPointerException e) {
        log.error("NullPointerException 异常:", e);
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler(MultipartException.class)
    public R<?> handleMultipartException(MultipartException e) {
        log.error("MultipartException 异常:", e);
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public R<?> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        log.error("MissingServletRequestParameterException 异常:", e);
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler(LoginExpireException.class)
    public R<?> handleLoginExpireException(LoginExpireException e) {
        log.error("TokenExpireException 异常:{}", e.getMessage());
        return R.fail(HttpCode.UNAUTHORIZED, e.getErrorCode(), e.getErrorMsg());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public R<?> handleUserNotFoundException(UserNotFoundException e) {
        log.error("UserNotFoundException 异常:{}", e.getMessage());
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler(UserPasswordErrorException.class)
    public R<?> handleUserPasswordErrorException(UserPasswordErrorException e) {
        log.error("UserPasswordErrorException 异常:{}", e.getMessage());
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler(UnknownHostException.class)
    public R<?> handleUnknownHostException(UnknownHostException e) {
        log.warn("UnknownHostException:{}", e.getMessage());
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler(LimitException.class)
    public R<?> handleLimitException(LimitException e) {
        log.warn("LimitException:{}", e.getErrorMsg());
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @Override
    public int getOrder() {
        return BeanOrderEnum.BASE_EXCEPTION_ORDER.getOrder();
    }
}
