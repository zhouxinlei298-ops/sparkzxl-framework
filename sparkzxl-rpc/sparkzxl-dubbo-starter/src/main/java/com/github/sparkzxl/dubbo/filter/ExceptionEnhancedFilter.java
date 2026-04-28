package com.github.sparkzxl.dubbo.filter;

import cn.hutool.core.exceptions.UtilException;
import cn.hutool.core.util.ClassLoaderUtil;
import com.github.sparkzxl.core.support.ArgumentException;
import com.github.sparkzxl.dubbo.support.DubboTransferException;
import com.github.sparkzxl.dubbo.support.ExceptionHandlerLoad;
import com.github.sparkzxl.dubbo.support.RpcFallbackException;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.common.logger.ErrorTypeAwareLogger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.JsonUtils;
import org.apache.dubbo.common.utils.ReflectUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.rpc.*;
import org.apache.dubbo.rpc.filter.ExceptionFilter;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.service.GenericService;
import org.apache.dubbo.rpc.support.RpcUtils;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.lang.reflect.Method;

import static org.apache.dubbo.common.constants.LoggerCodeConstants.CONFIG_FILTER_VALIDATION_EXCEPTION;

/**
 * description: 实现对 ExceptionFilter 增强的过滤器
 *
 * @author zhouxinlei
 */
@Slf4j
@Activate(group = {CommonConstants.PROVIDER, CommonConstants.CONSUMER}, order = -3)
public class ExceptionEnhancedFilter implements Filter, Filter.Listener {

    private final ErrorTypeAwareLogger logger = LoggerFactory.getErrorTypeAwareLogger(ExceptionFilter.class);

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        return invoker.invoke(invocation);
    }

    @Override
    public void onResponse(Result appResponse, Invoker<?> invoker, Invocation invocation) {
        // 发生异常，并且非泛化调用
        if (appResponse.hasException() && GenericService.class != invoker.getInterface()) {
            try {
                Throwable exception = appResponse.getException();

                // <1> 如果是参数校验的 ConstraintViolationException 异常，则封装返回
                if (exception instanceof ConstraintViolationException) {
                    appResponse.setException(this.handleConstraintViolationException((ConstraintViolationException) exception));
                    return;
                }
                // <2> 如果是参数 ArgumentException 异常，直接返回（异常已存在，无需重复设置）
                if (exception instanceof ArgumentException) {
                    appResponse.setException(new IllegalArgumentException(exception.getMessage()));
                    return;
                }
                // <3> 如果是RPC降级的异常，直接返回（异常已存在，无需重复设置）
                if (exception instanceof RpcFallbackException) {
                    return;
                }

                if (ExceptionHandlerLoad.contains(exception.getClass())) {
                    String exceptionJson = JsonUtils.toJson(exception);
                    String serviceName = getCurrentServiceName();
                    appResponse.setAttachment("ORIGINAL_EXCEPTION", exceptionJson);
                    appResponse.setAttachment("ORIGINAL_SERVICE_NAME", serviceName);
                    appResponse.setException(new DubboTransferException(
                            serviceName,
                            exception.getClass().getName(),
                            exception.getMessage()));
                    return;
                }
                if (exception instanceof DubboTransferException) {
                    String exceptionJson = appResponse.getAttachment("ORIGINAL_EXCEPTION");
                    String originalServiceName = appResponse.getAttachment("ORIGINAL_SERVICE_NAME");
                    DubboTransferException dubboTransferException = (DubboTransferException) exception;
                    logger.error("发生异常的服务：{}", originalServiceName);
                    String excClassName = dubboTransferException.getExClassName();
                    try {
                        Class<?> exClass = ClassLoaderUtil.loadClass(excClassName);
                        Throwable transferException = JsonUtils.toJavaObject(exceptionJson, exClass);
                        if (transferException != null) {
                            // 将异常设置回响应
                            appResponse.setException(transferException);
                            return;
                        }
                    } catch (UtilException e) {
                        logger.error("加载异常类失败：{}", e.getMessage());
                    }
                }

                // directly throw if it's checked exception
                if (!(exception instanceof RuntimeException) && (exception instanceof Exception)) {
                    return;
                }

                // directly throw if the exception appears in the signature
                try {
                    Method method = invoker.getInterface().getMethod(invocation.getMethodName(), invocation.getParameterTypes());
                    Class<?>[] exceptionClasses = method.getExceptionTypes();
                    for (Class<?> exceptionClass : exceptionClasses) {
                        if (exception.getClass().equals(exceptionClass)) {
                            return;
                        }
                    }
                } catch (NoSuchMethodException e) {
                    return;
                }

                // for the exception not found in method's signature, print ERROR message in server's log.
                logger.error(
                        CONFIG_FILTER_VALIDATION_EXCEPTION,
                        "",
                        "",
                        "Got unchecked and undeclared exception which called by "
                                + RpcContext.getServiceContext().getRemoteHost() + ". service: "
                                + invoker.getInterface().getName() + ", method: " + RpcUtils.getMethodName(invocation)
                                + ", exception: "
                                + exception.getClass().getName() + ": " + exception.getMessage(),
                        exception);

                // directly throw if exception class and interface class are in the same jar file.
                String serviceFile = ReflectUtils.getCodeBase(invoker.getInterface());
                String exceptionFile = ReflectUtils.getCodeBase(exception.getClass());
                if (serviceFile == null || exceptionFile == null || serviceFile.equals(exceptionFile)) {
                    return;
                }
                // directly throw if it's JDK exception
                String className = exception.getClass().getName();
                if (className.startsWith("java.")
                        || className.startsWith("javax.")
                        || className.startsWith("jakarta.")) {
                    return;
                }
                // directly throw if it's dubbo exception
                if (exception instanceof RpcException) {
                    return;
                }

                // otherwise, wrap with RuntimeException and throw back to the client
                appResponse.setException(new RuntimeException(StringUtils.toString(exception)));
            } catch (Throwable e) {
                logger.warn(
                        CONFIG_FILTER_VALIDATION_EXCEPTION,
                        "",
                        "",
                        "Fail to ExceptionFilter when called by "
                                + RpcContext.getServiceContext().getRemoteHost() + ". service: "
                                + invoker.getInterface().getName() + ", method: " + RpcUtils.getMethodName(invocation)
                                + ", exception: "
                                + e.getClass().getName() + ": " + e.getMessage(),
                        e);
            }
        }
    }

    /**
     * 将 MethodValidatedException 转换成 ServiceException
     *
     * @param e MethodValidatedException
     * @return ArgumentException
     */
    private IllegalArgumentException handleConstraintViolationException(ConstraintViolationException e) {
        // 拼接错误
        StringBuilder detailMessage = new StringBuilder();
        for (ConstraintViolation<?> constraintViolation : e.getConstraintViolations()) {
            // 使用 ; 分隔多个错误
            if (detailMessage.length() > 0) {
                detailMessage.append(";");
            }
            // 拼接内容到其中
            detailMessage.append(constraintViolation.getMessage());

        }
        // 返回异常
        return new IllegalArgumentException(detailMessage.toString());
    }

    @Override
    public void onError(Throwable e, Invoker<?> invoker, Invocation invocation) {
        logger.error(
                CONFIG_FILTER_VALIDATION_EXCEPTION,
                "",
                "",
                "Got unchecked and undeclared exception which called by "
                        + RpcContext.getServiceContext().getRemoteHost() + ". service: "
                        + invoker.getInterface().getName() + ", method: " + RpcUtils.getMethodName(invocation)
                        + ", exception: "
                        + e.getClass().getName() + ": " + e.getMessage(),
                e);
    }

    /**
     * 获取当前服务名称
     *
     * @return String
     */
    private String getCurrentServiceName() {
        String applicationName = ApplicationModel.defaultModel().getApplicationName();
        return applicationName != null ? applicationName : "unknown-server";
    }

}
