package com.github.sparkzxl.dubbo.filter;

import com.github.sparkzxl.core.spring.SpringContextUtils;
import com.github.sparkzxl.core.support.code.ExceptionErrorCode;
import com.github.sparkzxl.dubbo.properties.DubboConsumerProperties;
import com.github.sparkzxl.dubbo.support.ServiceException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.filter.ClusterFilter;
import org.apache.dubbo.rpc.support.RpcUtils;

/**
 * description: 系统异常将降级处理过滤器
 *
 * @author zhouxinlei
 * @version 1.0
 * @since 2026-03-19 14:57:48
 */
@Slf4j
@Getter
@Setter
@Activate(group = CommonConstants.CONSUMER, order = -10000)
public class ServiceFallbackFilter implements ClusterFilter {

    private DubboConsumerProperties consumerProperties;

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        if (ObjectUtils.isEmpty(consumerProperties)) {
            DubboConsumerProperties consumerProperties = SpringContextUtils.getBean(DubboConsumerProperties.class);
            setConsumerProperties(consumerProperties);
        }
        try {
            return invoker.invoke(invocation);
        } catch (RpcException e) {
            if (consumerProperties.isFallback()) {
                String serviceName = invoker.getInterface().getName();
                String methodName = invocation.getMethodName();
                log.error("[Consumer异常] 服务: {}, 方法: {}, 异常: {}", serviceName, RpcUtils.getMethodName(invocation), e.getMessage(), e);
                String dubboService = invoker.getInterface().getName() + "#" + methodName;
                throw handleDubboFallback(dubboService, methodName, e);
            } else {
                throw e;
            }
        }
    }

    public ServiceException handleDubboFallback(String serviceName, String methodName, RpcException e) {
        String errorCode;
        String message;

        switch (e.getCode()) {
            case RpcException.FORBIDDEN_EXCEPTION:
                if (e.getMessage().contains("No provider available")) {
                    errorCode = ExceptionErrorCode.OPEN_SERVICE_UNAVAILABLE.getErrorCode();
                    message = "[" + serviceName + "]服务不可用";
                } else {
                    errorCode = ExceptionErrorCode.RPC_SERVICE_EXCEPTION.getErrorCode();
                    message = "[" + serviceName + "]调用异常";
                }
                break;
            case RpcException.TIMEOUT_EXCEPTION:
                errorCode = ExceptionErrorCode.TIME_OUT_ERROR.getErrorCode();
                message = "[" + serviceName + "]调用超时";
                break;
            case RpcException.NETWORK_EXCEPTION:
                errorCode = ExceptionErrorCode.FAILURE.getErrorCode();
                message = "[" + serviceName + "]网络异常";
                break;
            default:
                errorCode = ExceptionErrorCode.RPC_SERVICE_EXCEPTION.getErrorCode();
                message = "[" + serviceName + "]调用异常";
        }

        log.error("[服务降级] 服务: {}, 方法: {}, 错误码: {}, 错误: {}", serviceName, methodName, errorCode, e.getMessage());
        return new ServiceException(errorCode, message + "，请稍后重试");
    }
}
