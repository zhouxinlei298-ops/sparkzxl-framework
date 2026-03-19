package com.github.sparkzxl.dubbo.support;

import com.github.sparkzxl.core.base.result.R;
import com.github.sparkzxl.core.constant.enums.BeanOrderEnum;
import com.github.sparkzxl.core.support.code.ExceptionErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.rpc.RpcException;
import org.springframework.core.Ordered;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

/**
 * description: 全局异常处理
 *
 * @author zhouxinlei
 */
@ControllerAdvice
@RestController
@Slf4j
public class DubboExceptionHandler implements Ordered {

    @ExceptionHandler(RpcException.class)
    public R<?> handleRpcException(RpcException e) {
        log.error("DUBBO接口异常:{}", e.getMessage());
        return R.failDetail(ExceptionErrorCode.RPC_SERVICE_EXCEPTION.getErrorCode(), e.getMessage());
    }

    @Override
    public int getOrder() {
        return BeanOrderEnum.BASE_EXCEPTION_ORDER.getOrder() - 1;
    }
}
