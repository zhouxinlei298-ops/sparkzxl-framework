package com.github.sparkzxl.web.support;

import cn.hutool.core.util.StrUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.github.sparkzxl.core.support.code.ExceptionErrorCode;
import com.github.sparkzxl.core.support.ExceptionCodeResolver;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.util.NestedServletException;

import javax.security.auth.login.AccountNotFoundException;
import javax.servlet.ServletException;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.ValidationException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Web 基础异常码注册
 *
 * @author zhouxinlei
 */
@Component
public class WebExceptionCodeRegistration implements InitializingBean {

    @Override
    public void afterPropertiesSet() {
        // ===== A 类：静态映射 =====
        ExceptionCodeResolver.register(NullPointerException.class, ExceptionErrorCode.NULL_POINTER_EXCEPTION_ERROR);
        ExceptionCodeResolver.register(MultipartException.class, ExceptionErrorCode.FILE_UPLOAD_ERROR);
        ExceptionCodeResolver.register(AccountNotFoundException.class, ExceptionErrorCode.USER_NOT_FOUND);
        ExceptionCodeResolver.register(HttpRequestMethodNotSupportedException.class, ExceptionErrorCode.METHOD_NOT_SUPPORTED);
        ExceptionCodeResolver.register(UnknownHostException.class, ExceptionErrorCode.IP_OR_DOMAIN_NAME_UNREACHABLE);
        ExceptionCodeResolver.register(IllegalArgumentException.class, ExceptionErrorCode.PARAM_VALID_ERROR);
        ExceptionCodeResolver.register(IllegalStateException.class, ExceptionErrorCode.PARAM_VALID_ERROR);
        ExceptionCodeResolver.register(NestedServletException.class, ExceptionErrorCode.FAILURE);

        // ===== C 类：消息谓词匹配 =====
        ExceptionCodeResolver.register(ServletException.class,
                "UT010016: Not a multi part request"::equalsIgnoreCase,
                ExceptionErrorCode.FILE_UPLOAD_ERROR);
        ExceptionCodeResolver.register(ServletException.class, ExceptionErrorCode.FAILURE);

        // ===== D 类：自定义解析函数 =====
        ExceptionCodeResolver.registerResolver(ConstraintViolationException.class, this::resolveConstraintViolation);
        ExceptionCodeResolver.registerResolver(MethodArgumentNotValidException.class, this::resolveMethodArgumentNotValid);
        ExceptionCodeResolver.registerResolver(BindException.class, this::resolveBindException);
        ExceptionCodeResolver.registerResolver(HttpMessageNotReadableException.class, this::resolveHttpMessageNotReadable);
        ExceptionCodeResolver.registerResolver(InvalidFormatException.class, this::resolveInvalidFormat);
        ExceptionCodeResolver.registerResolver(NoHandlerFoundException.class, this::resolveNoHandlerFound);
        ExceptionCodeResolver.registerResolver(HttpMediaTypeNotSupportedException.class, this::resolveMediaTypeNotSupported);
        ExceptionCodeResolver.registerResolver(MethodArgumentTypeMismatchException.class, this::resolveMethodArgumentTypeMismatch);
        ExceptionCodeResolver.registerResolver(MissingServletRequestParameterException.class, this::resolveMissingServletRequestParameter);
        ExceptionCodeResolver.registerResolver(ValidationException.class, this::resolveValidation);
    }

    private ExceptionCodeResolver.ResolvedError resolveConstraintViolation(Throwable ex) {
        ConstraintViolationException cve = (ConstraintViolationException) ex;
        Set<ConstraintViolation<?>> violations = cve.getConstraintViolations();
        String message = violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining(";"));
        return new ExceptionCodeResolver.ResolvedError(ExceptionErrorCode.PARAM_VALID_ERROR.getErrorCode(), message);
    }

    private ExceptionCodeResolver.ResolvedError resolveMethodArgumentNotValid(Throwable ex) {
        MethodArgumentNotValidException methodArgumentNotValidException = (MethodArgumentNotValidException) ex;
        BindingResult bindingResult = methodArgumentNotValidException.getBindingResult();
        List<ObjectError> allErrors = bindingResult.getAllErrors();
        String message;
        if (CollectionUtils.isNotEmpty(allErrors)) {
            message = allErrors.get(0).getDefaultMessage() == null ? "" : allErrors.get(0).getDefaultMessage();
        } else {
            message = ExceptionErrorCode.PARAM_MISS.getErrorMsg();
        }
        return new ExceptionCodeResolver.ResolvedError(ExceptionErrorCode.PARAM_VALID_ERROR.getErrorCode(), message);
    }

    private ExceptionCodeResolver.ResolvedError resolveBindException(Throwable ex) {
        BindException be = (BindException) ex;
        FieldError fieldError = be.getBindingResult().getFieldError();
        if (fieldError != null && StrUtil.isNotEmpty(fieldError.getDefaultMessage())) {
            return new ExceptionCodeResolver.ResolvedError(ExceptionErrorCode.PARAM_EX.getErrorCode(),
                    fieldError.getDefaultMessage());
        }
        StringBuilder msg = new StringBuilder();
        List<FieldError> fieldErrors = be.getFieldErrors();
        fieldErrors.forEach(oe ->
                msg.append("参数:[").append(oe.getObjectName())
                        .append(".").append(oe.getField())
                        .append("]的传入值:[").append(oe.getRejectedValue()).append("]与预期的字段类型不匹配."));
        return new ExceptionCodeResolver.ResolvedError(ExceptionErrorCode.PARAM_EX.getErrorCode(), msg.toString());
    }

    private ExceptionCodeResolver.ResolvedError resolveHttpMessageNotReadable(Throwable ex) {
        String message = ex.getMessage();
        if (StringUtils.isNotEmpty(message)) {
            String prefix = "Could not read document:";
            if (StrUtil.containsAny(message, prefix)) {
                String errorMessage = String.format("无法正确的解析json类型的参数：%s", StrUtil.subBetween(message, prefix, " at "));
                return new ExceptionCodeResolver.ResolvedError(ExceptionErrorCode.PARAM_VALID_ERROR.getErrorCode(), errorMessage);
            }
            Pattern pattern = Pattern.compile("Cannot deserialize value of type `([^`]+)` from String \"([^\"]+)\"");
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                String type = matcher.group(1);
                String value = matcher.group(2);
                String errorMessage = "参数值[" + value + "]与预期字段类型:[" + type + "]不匹配";
                return new ExceptionCodeResolver.ResolvedError(ExceptionErrorCode.PARAM_VALID_ERROR.getErrorCode(), errorMessage);
            }
        }
        return new ExceptionCodeResolver.ResolvedError(ExceptionErrorCode.MSG_NOT_READABLE.getErrorCode(),
                ExceptionErrorCode.MSG_NOT_READABLE.getErrorMsg());
    }

    private ExceptionCodeResolver.ResolvedError resolveInvalidFormat(Throwable ex) {
        InvalidFormatException e = (InvalidFormatException) ex;
        String message = e.getMessage();
        Pattern pattern = Pattern.compile("Cannot deserialize value of type `([^`]+)` from String \"([^\"]+)\"");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            String type = matcher.group(1);
            String value = matcher.group(2);
            return new ExceptionCodeResolver.ResolvedError(ExceptionErrorCode.PARAM_VALID_ERROR.getErrorCode(),
                    "参数值[" + value + "]与预期字段类型:[" + type + "]不匹配");
        }
        return new ExceptionCodeResolver.ResolvedError(ExceptionErrorCode.PARAM_VALID_ERROR.getErrorCode(), message);
    }

    private ExceptionCodeResolver.ResolvedError resolveNoHandlerFound(Throwable ex) {
        return new ExceptionCodeResolver.ResolvedError(ExceptionErrorCode.NOT_FOUND.getErrorCode(), ex.getMessage());
    }

    private ExceptionCodeResolver.ResolvedError resolveMediaTypeNotSupported(Throwable ex) {
        HttpMediaTypeNotSupportedException e = (HttpMediaTypeNotSupportedException) ex;
        MediaType contentType = e.getContentType();
        if (contentType != null) {
            return new ExceptionCodeResolver.ResolvedError(
                    ExceptionErrorCode.MEDIA_TYPE_NOT_SUPPORTED.getErrorCode(),
                    "请求类型(Content-Type)[" + contentType + "] 与实际接口的请求类型不匹配");
        }
        return new ExceptionCodeResolver.ResolvedError(
                ExceptionErrorCode.MEDIA_TYPE_NOT_SUPPORTED.getErrorCode(),
                ExceptionErrorCode.MEDIA_TYPE_NOT_SUPPORTED.getErrorMsg());
    }

    private ExceptionCodeResolver.ResolvedError resolveMethodArgumentTypeMismatch(Throwable ex) {
        MethodArgumentTypeMismatchException e = (MethodArgumentTypeMismatchException) ex;
        String msg = "参数：[" + e.getName() + "]的传入值：[" + e.getValue() +
                "]与预期的字段类型：[" + Objects.requireNonNull(e.getRequiredType()).getName() + "]不匹配";
        return new ExceptionCodeResolver.ResolvedError(ExceptionErrorCode.PARAM_TYPE_ERROR.getErrorCode(), msg);
    }

    private ExceptionCodeResolver.ResolvedError resolveMissingServletRequestParameter(Throwable ex) {
        MissingServletRequestParameterException e = (MissingServletRequestParameterException) ex;
        String message = "缺少必须的[" + e.getParameterType() + "]类型的参数[" + e.getParameterName() + "]";
        return new ExceptionCodeResolver.ResolvedError(ExceptionErrorCode.PARAM_MISS.getErrorCode(), message);
    }

    private ExceptionCodeResolver.ResolvedError resolveValidation(Throwable ex) {
        ValidationException e = (ValidationException) ex;
        return new ExceptionCodeResolver.ResolvedError(ExceptionErrorCode.PARAM_VALID_ERROR.getErrorCode(), e.getMessage());
    }
}
