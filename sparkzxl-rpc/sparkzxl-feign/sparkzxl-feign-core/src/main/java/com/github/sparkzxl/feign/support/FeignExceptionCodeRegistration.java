package com.github.sparkzxl.feign.support;

import cn.hutool.core.lang.Opt;
import cn.hutool.core.text.StrFormatter;
import com.github.sparkzxl.core.support.code.ExceptionErrorCode;
import com.github.sparkzxl.core.support.ExceptionCodeResolver;
import com.github.sparkzxl.feign.exception.RemoteCallTransferException;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.RetryableException;
import feign.Target;
import feign.codec.DecodeException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.net.SocketTimeoutException;

/**
 * Feign 异常码注册
 *
 * @author zhouxinlei
 */
@Component
public class FeignExceptionCodeRegistration implements InitializingBean {

    @Override
    public void afterPropertiesSet() {
        // ===== A 类：静态映射 =====
        ExceptionCodeResolver.register(DecodeException.class, ExceptionErrorCode.DECODE_EXCEPTION);
        ExceptionCodeResolver.register(SocketTimeoutException.class, ExceptionErrorCode.TIME_OUT_ERROR);

        // ===== D 类：自定义解析函数 =====
        ExceptionCodeResolver.registerResolver(RetryableException.class, this::resolveRetryable);
        ExceptionCodeResolver.registerResolver(FeignException.ServiceUnavailable.class, this::resolveServiceUnavailable);
        ExceptionCodeResolver.registerResolver(RemoteCallTransferException.class, this::resolveRemoteCallTransfer);
    }

    private ExceptionCodeResolver.ResolvedError resolveRetryable(Throwable ex) {
        RetryableException e = (RetryableException) ex;
        String applicationName = Opt.ofNullable(e.request()).map(Request::requestTemplate)
                .map(RequestTemplate::feignTarget).map(Target::name).orElse("unKnownServer");
        String message = StrFormatter.format(ExceptionErrorCode.RETRY_ABLE_EXCEPTION.getErrorMsg(), applicationName);
        return new ExceptionCodeResolver.ResolvedError(ExceptionErrorCode.RETRY_ABLE_EXCEPTION.getErrorCode(), message);
    }

    private ExceptionCodeResolver.ResolvedError resolveServiceUnavailable(Throwable ex) {
        FeignException.ServiceUnavailable e = (FeignException.ServiceUnavailable) ex;
        String applicationName = Opt.ofNullable(e.request()).map(Request::requestTemplate)
                .map(RequestTemplate::feignTarget).map(Target::name).orElse("unKnownServer");
        String message = StrFormatter.format(ExceptionErrorCode.OPEN_SERVICE_UNAVAILABLE.getErrorMsg(), applicationName);
        return new ExceptionCodeResolver.ResolvedError(ExceptionErrorCode.OPEN_SERVICE_UNAVAILABLE.getErrorCode(), message);
    }

    private ExceptionCodeResolver.ResolvedError resolveRemoteCallTransfer(Throwable ex) {
        RemoteCallTransferException e = (RemoteCallTransferException) ex;
        String applicationName = Opt.ofNullable(e.request()).map(Request::requestTemplate)
                .map(RequestTemplate::feignTarget).map(Target::name).orElse("unKnownServer");
        String message = StrFormatter.format("【{}】异常,{}", applicationName, e.getErrorMsg());
        return new ExceptionCodeResolver.ResolvedError(e.getErrorCode(), message);
    }
}
