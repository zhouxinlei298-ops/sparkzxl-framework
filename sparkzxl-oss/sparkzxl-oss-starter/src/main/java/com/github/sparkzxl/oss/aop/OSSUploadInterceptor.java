package com.github.sparkzxl.oss.aop;

import com.github.sparkzxl.core.spring.SpringContextUtils;
import com.github.sparkzxl.oss.annotation.OSSUpload;
import com.github.sparkzxl.oss.listener.UploadListener;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.aop.framework.AopProxyUtils;

import java.util.Objects;

/**
 * description: OSS Upload Interceptor
 *
 * @author zhouxinlei
 * @since 2022-09-27 09:28:55
 */
@Slf4j
public class OSSUploadInterceptor implements MethodInterceptor {

    @Nullable
    @Override
    public Object invoke(@NotNull MethodInvocation invocation) throws Throwable {
        //fix 使用其他aop组件时,aop切了两次.
        Class<?> cls = AopProxyUtils.ultimateTargetClass(Objects.requireNonNull(invocation.getThis()));
        if (!cls.equals(invocation.getThis().getClass())) {
            return invocation.proceed();
        }
        OSSUpload annotation = invocation.getMethod().getAnnotation(OSSUpload.class);
        if (annotation == null || !annotation.enabled()) {
            // 注解禁用或不存在时，直接执行原方法，不做拦截处理
            log.debug("OSS upload annotation is disabled or not present for method: {}, proceeding with original method",
                    invocation.getMethod().getName());
            return invocation.proceed();
        }
        Class<? extends UploadListener> listener = annotation.listener();
        UploadListener uploadListener = SpringContextUtils.getBean(listener);
        try {
            uploadListener.onListener(invocation);
            return invocation.proceed();
        } finally {
            uploadListener.afterListener(invocation);
        }
    }
}
