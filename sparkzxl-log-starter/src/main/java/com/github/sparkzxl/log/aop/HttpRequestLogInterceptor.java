package com.github.sparkzxl.log.aop;

import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.URLUtil;
import com.github.sparkzxl.core.context.RequestContextHelper;
import com.github.sparkzxl.core.context.RequestLocalContextHolder;
import com.github.sparkzxl.core.json.JsonUtils;
import com.github.sparkzxl.core.spring.SpringContextUtils;
import com.github.sparkzxl.core.util.AopUtil;
import com.github.sparkzxl.core.util.NetworkUtil;
import com.github.sparkzxl.log.annotation.HttpRequestLog;
import com.github.sparkzxl.log.entity.RequestInfoLog;
import com.github.sparkzxl.log.event.HttpRequestLogEvent;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.lang3.ObjectUtils;

import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * HTTP 请求日志拦截器，基于 MethodInterceptor 实现
 * <p>
 * 将 @Before/@AfterReturning/@AfterThrowing 三段式通知合并为单一 invoke() 方法，
 * 使用局部变量替代 ThreadLocal，从根源上避免空指针问题。
 *
 * @author zhouxinlei
 */
public class HttpRequestLogInterceptor implements MethodInterceptor {

    public static final int MAX_LENGTH = 65535;

    @Override
    public Object invoke(@Nonnull MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        HttpRequestLog httpRequestLog = method.getAnnotation(HttpRequestLog.class);
        if (httpRequestLog == null || !httpRequestLog.enabled()) {
            return invocation.proceed();
        }

        HttpServletRequest httpServletRequest = RequestContextHelper.currentHttpServletRequest();
        RequestInfoLog requestInfoLog = buildRequestInfoLog(httpServletRequest, invocation, httpRequestLog);

        Object result;
        try {
            result = invocation.proceed();
        } catch (Throwable e) {
            requestInfoLog.setErrorMsg(ExceptionUtil.stacktraceToString(e, MAX_LENGTH));
            requestInfoLog.setThrowExceptionClass(e.getClass().getTypeName());
            publishEvent(requestInfoLog);
            throw e;
        }

        if (httpRequestLog.response() && ObjectUtils.isNotEmpty(result)) {
            requestInfoLog.setResult(JsonUtils.getJson().toJson(result));
        }
        publishEvent(requestInfoLog);
        return result;
    }

    private void publishEvent(RequestInfoLog requestInfoLog) {
        LocalDateTime finishTime = LocalDateTime.now();
        requestInfoLog.setFinishTime(finishTime);
        requestInfoLog.setConsumingTime(requestInfoLog.getStartTime().until(finishTime, ChronoUnit.MILLIS));
        SpringContextUtils.publishEvent(new HttpRequestLogEvent(requestInfoLog));
    }

    private RequestInfoLog buildRequestInfoLog(HttpServletRequest request, MethodInvocation invocation, HttpRequestLog httpRequestLog) {
        String userId = RequestLocalContextHolder.getUserId(String.class);
        String name = RequestLocalContextHolder.getName();
        Method method = invocation.getMethod();
        RequestInfoLog requestInfoLog = new RequestInfoLog()
                .setCategory(httpRequestLog.value())
                .setUserId(userId)
                .setUserName(name)
                .setIp(NetworkUtil.getIpAddress(request))
                .setRequestUrl(URLUtil.getPath(request.getRequestURI()))
                .setHttpMethod(request.getMethod())
                .setClassMethod(String.format("%s.%s", method.getDeclaringClass().getName(), method.getName()))
                .setStartTime(LocalDateTime.now())
                .setTenantId(RequestLocalContextHolder.getTenantId())
                .setTraceId(RequestLocalContextHolder.traceId());
        if (httpRequestLog.request()) {
            Map<String, Object> parameterMap = AopUtil.getParameterMap(method, invocation.getArguments(), httpRequestLog.excludeClass());
            if (MapUtil.isNotEmpty(parameterMap)) {
                requestInfoLog.setRequestParams(JsonUtils.getJson().toJson(parameterMap));
            }
        }
        return requestInfoLog;
    }
}
