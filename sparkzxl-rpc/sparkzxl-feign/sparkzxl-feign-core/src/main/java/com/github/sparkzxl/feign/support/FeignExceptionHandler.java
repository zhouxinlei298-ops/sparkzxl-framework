package com.github.sparkzxl.feign.support;

import com.github.sparkzxl.core.base.result.R;
import com.github.sparkzxl.core.constant.enums.BeanOrderEnum;
import com.github.sparkzxl.core.support.ExceptionCodeResolver;
import com.github.sparkzxl.feign.exception.RemoteCallTransferException;
import feign.*;
import feign.codec.DecodeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.SocketTimeoutException;

/**
 * description: Springboot WEB应用全局异常处理
 *
 * @author zhouxinlei
 */
@Slf4j
@RestControllerAdvice
public class FeignExceptionHandler implements Ordered {

    @ExceptionHandler(SocketTimeoutException.class)
    public R<?> handleSocketTimeoutException(SocketTimeoutException e) {
        log.error("SocketTimeoutException 异常:", e);
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler(RetryableException.class)
    public R<?> handleRetryableException(RetryableException e) {
        log.error("RetryableException 异常:", e);
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }


    @ExceptionHandler(FeignException.ServiceUnavailable.class)
    public R<?> handleServiceUnavailableException(FeignException.ServiceUnavailable e) {
        log.error("ServiceUnavailable异常:", e);
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler(DecodeException.class)
    public R<?> handleDecodeException(DecodeException e) {
        log.error("DecodeException 异常:", e);
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler(RemoteCallTransferException.class)
    public R<?> handleRemoteCallException(RemoteCallTransferException e) {
        log.error("RemoteCallTransferException 异常:", e);
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @Override
    public int getOrder() {
        return BeanOrderEnum.FEIGN_EXCEPTION_ORDER.getOrder();
    }
}
