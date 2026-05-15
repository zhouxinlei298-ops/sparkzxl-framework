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
            String exceptionClassName = resolveExceptionClassName(joinPoint);
            if (exceptionClassName != null) {
                MDC.put("exceptionClass", exceptionClassName);
            }
            return joinPoint.proceed();
        } finally {
            MDC.remove("exceptionClass");
        }
    }

    private String resolveExceptionClassName(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof Throwable) {
                    return arg.getClass().getName();
                }
            }
        }
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        ExceptionHandler annotation = AnnotationUtil.getAnnotation(method, ExceptionHandler.class);
        if (annotation != null && annotation.value().length > 0) {
            return annotation.value()[0].getName();
        }
        return null;
    }
}
