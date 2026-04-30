package com.github.sparkzxl.dubbo.support;

import com.github.sparkzxl.core.base.result.R;
import com.github.sparkzxl.core.constant.enums.BeanOrderEnum;
import com.github.sparkzxl.core.support.ExceptionCodeResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

/**
 * description: dubbo全局异常处理
 *
 * @author zhouxinlei
 * @version 1.0
 * @since 2026-04-30 14:16:25
 */
@ControllerAdvice
@RestController
@Slf4j
public class DubboExceptionHandler implements Ordered {

    @ExceptionHandler(RpcFallbackException.class)
    public R<?> handleRpcFallbackException(RpcFallbackException e) {
        log.error("DUBBO 服务异常:{}", e.getMessage());
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler(RpcCaptureException.class)
    public R<?> handleRpcCaptureException(RpcCaptureException e) {
        log.error("DUBBO 捕获服务:{},异常:{}", e.getServiceName(), e.getMessage());
        String errorMessage = "【" + e.getServiceName() + "】异常：" + e.getMessage();
        return R.failDetail(e.getErrorCode(), errorMessage);
    }

    @Override
    public int getOrder() {
        return BeanOrderEnum.DUBBO_EXCEPTION_ORDER.getOrder();
    }
}
