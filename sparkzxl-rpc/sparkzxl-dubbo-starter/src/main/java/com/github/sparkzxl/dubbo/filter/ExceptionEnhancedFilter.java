package com.github.sparkzxl.dubbo.filter;

import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.common.logger.ErrorTypeAwareLogger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.ReflectUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.rpc.*;
import org.apache.dubbo.rpc.filter.ExceptionFilter;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.service.GenericService;
import org.apache.dubbo.rpc.support.RpcUtils;
import com.github.sparkzxl.core.support.code.ExceptionErrorCode;
import com.github.sparkzxl.core.support.ExceptionCodeResolver;
import com.github.sparkzxl.dubbo.support.RpcCaptureException;
import com.github.sparkzxl.dubbo.support.RpcFallbackException;

import java.lang.reflect.Method;

import static org.apache.dubbo.common.constants.LoggerCodeConstants.CONFIG_FILTER_VALIDATION_EXCEPTION;

@Slf4j
@Activate(group = {CommonConstants.PROVIDER, CommonConstants.CONSUMER}, order = -3)
public class ExceptionEnhancedFilter implements Filter, Filter.Listener {

    private final ErrorTypeAwareLogger logger = LoggerFactory.getErrorTypeAwareLogger(ExceptionFilter.class);

    private static final String ATTACHMENT_ERROR_CODE = "RPC_ERROR_CODE";
    private static final String ATTACHMENT_ERROR_MESSAGE = "RPC_ERROR_MESSAGE";
    private static final String ATTACHMENT_ORIGIN_SERVICE = "RPC_ORIGIN_SERVICE";

    private final ThreadLocal<String> rpcSide = ThreadLocal.withInitial(() -> "");

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        RpcServiceContext context = RpcContext.getServiceContext();
        if (context.isProviderSide()) {
            rpcSide.set(CommonConstants.PROVIDER);
        } else if (context.isConsumerSide()) {
            rpcSide.set(CommonConstants.CONSUMER);
        }
        return invoker.invoke(invocation);
    }

    @Override
    public void onResponse(Result appResponse, Invoker<?> invoker, Invocation invocation) {
        if (appResponse.hasException() && GenericService.class != invoker.getInterface()) {
            try {
                Throwable exception = appResponse.getException();
                if (CommonConstants.PROVIDER.equalsIgnoreCase(rpcSide.get())){
                    appResponse.setException(handleProviderException(appResponse,exception));
                }

                if (CommonConstants.CONSUMER.equalsIgnoreCase(rpcSide.get())){
                    String rpcErrorCode = appResponse.getAttachment(ATTACHMENT_ERROR_CODE);
                    if (StringUtils.isNotEmpty(rpcErrorCode)){
                        String rpcErrorMessage = appResponse.getAttachment(ATTACHMENT_ERROR_MESSAGE);
                        String rpcOriginService = appResponse.getAttachment(ATTACHMENT_ORIGIN_SERVICE, getCurrentServiceName());
                        appResponse.setException(new RpcCaptureException(rpcOriginService,rpcErrorCode,rpcErrorMessage));
                        return;
                    }
                }

                if (exception instanceof RpcFallbackException) {
                    return;
                }

                if (!(exception instanceof RuntimeException) && (exception instanceof Exception)) {
                    return;
                }

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

                String serviceFile = ReflectUtils.getCodeBase(invoker.getInterface());
                String exceptionFile = ReflectUtils.getCodeBase(exception.getClass());
                if (serviceFile == null || exceptionFile == null || serviceFile.equals(exceptionFile)) {
                    return;
                }
                String className = exception.getClass().getName();
                if (className.startsWith("java.")
                        || className.startsWith("javax.")
                        || className.startsWith("jakarta.")) {
                    return;
                }
                if (exception instanceof RpcException) {
                    return;
                }

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

    private Exception handleProviderException(Result appResponse, Throwable exception) {
        ExceptionCodeResolver.ResolvedError resolved = ExceptionCodeResolver.resolve(exception,
                ExceptionErrorCode.RPC_SERVICE_EXCEPTION);
        appResponse.setAttachment(ATTACHMENT_ERROR_CODE, resolved.errorCode());
        appResponse.setAttachment(ATTACHMENT_ERROR_MESSAGE, resolved.errorMessage());
        appResponse.setAttachment(ATTACHMENT_ORIGIN_SERVICE, getCurrentServiceName());
        return new RuntimeException(resolved.errorMessage());
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

    private String getCurrentServiceName() {
        String applicationName = ApplicationModel.defaultModel().getApplicationName();
        return applicationName != null ? applicationName : "unknown-server";
    }
}
