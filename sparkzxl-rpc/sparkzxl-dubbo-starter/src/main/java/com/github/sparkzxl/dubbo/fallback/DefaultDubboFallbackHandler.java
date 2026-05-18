package com.github.sparkzxl.dubbo.fallback;

import com.github.sparkzxl.core.support.code.ExceptionErrorCode;
import com.github.sparkzxl.dubbo.support.RpcFallbackException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.rpc.*;

/**
 * 默认Dubbo服务降级处理器
 * <p>
 * 封装系统级降级逻辑，将RpcException转换为友好的RpcFallbackException并返回空结果。
 * 作为降级处理的兜底实现，当没有配置自定义降级处理器时使用。
 * </p>
 *
 * @author zhouxinlei
 * @since 2026-03-20
 */
@Slf4j
public enum DefaultDubboFallbackHandler implements DubboFallbackHandler {

    /**
     * 单例实例
     */
    INSTANCE;

    @Override
    public Result handle(Invoker<?> invoker, Invocation invocation, RpcException exception, String clientName) {
        String methodName = invocation.getMethodName();
        String interfaceMethodName = invoker.getInterface().getName() + "#" + methodName;

        String errorCode;
        String message;

        if (exception.isTimeout()) {
            errorCode = ExceptionErrorCode.TIME_OUT_ERROR.getErrorCode();
            message = "服务接口" + interfaceMethodName + "调用超时";
        } else if (exception.isNetwork()) {
            errorCode = ExceptionErrorCode.FAILURE.getErrorCode();
            message = "服务接口" + interfaceMethodName + "网络异常";
        } else if (exception.isLimitExceed()) {
            errorCode = ExceptionErrorCode.SYSTEM_BLOCK.getErrorCode();
            message = "服务接口" + interfaceMethodName + "繁忙";
        } else if (exception.isNoInvokerAvailableAfterFilter()) {
            errorCode = ExceptionErrorCode.OPEN_SERVICE_UNAVAILABLE.getErrorCode();
            message = "服务接口" + interfaceMethodName + "无可用提供者";
        } else {
            if (StringUtils.contains(exception.getMessage(),"No provider available")) {
                errorCode = ExceptionErrorCode.OPEN_SERVICE_UNAVAILABLE.getErrorCode();
                message = "服务接口" + interfaceMethodName + "不可用";
            } else {
                errorCode = ExceptionErrorCode.RPC_SERVICE_EXCEPTION.getErrorCode();
                message = "服务接口" + interfaceMethodName + "调用异常";
            }
        }
        log.error("[服务降级]  接口方法: {},  错误码: {}, 错误: {}",
                interfaceMethodName, errorCode, exception.getMessage());

        RpcFallbackException fallbackException = new RpcFallbackException(errorCode, message);
        return AsyncRpcResult.newDefaultAsyncResult(null, fallbackException, invocation);
    }
}
