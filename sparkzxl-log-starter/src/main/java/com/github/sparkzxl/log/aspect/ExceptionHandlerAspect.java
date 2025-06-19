package com.github.sparkzxl.log.aspect;

import cn.hutool.core.annotation.AnnotationUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * description: 异常处理切面
 *
 * @author zhouxinlei
 * @since 2025-06-19 16:17:48
 */
@Aspect
public class ExceptionHandlerAspect {

    @Pointcut("@within(org.springframework.web.bind.annotation.ExceptionHandler)|| @annotation(org.springframework.web.bind.annotation.ExceptionHandler)")
    public void pointCut() {

    }

    @Around("pointCut()")
    public Object aroundExceptionHandler(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
            ExceptionHandler annotation = AnnotationUtil.getAnnotation(method, ExceptionHandler.class);
            Class<? extends Throwable>[] classes = annotation.value();
            // 设置MDC
            MDC.put("exceptionClass", Arrays.stream(classes).findFirst().get().getName());
            // 执行原异常处理方法
            return joinPoint.proceed();
        } finally {
            // 清除MDC
            MDC.remove("exceptionClass");
        }
    }
}
