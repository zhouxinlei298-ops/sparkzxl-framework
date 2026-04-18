package com.github.sparkzxl.log.utils;

import cn.hutool.core.annotation.AnnotationUtil;
import com.github.sparkzxl.log.annotation.HttpRequestLog;
import java.lang.reflect.Method;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;

/**
 * description: 日志工具类
 *
 * @author zhouxinlei
 * @since 2022-03-20 11:39:03
 */
@Slf4j
public class LogUtils {

    /**
     * 从方法上获取 @HttpRequestLog 注解
     */
    public static HttpRequestLog getTargetAnnotation(JoinPoint point) {
        try {
            if (point.getSignature() instanceof MethodSignature) {
                Method method = ((MethodSignature) point.getSignature()).getMethod();
                return AnnotationUtil.getAnnotation(method, HttpRequestLog.class);
            }
            return null;
        } catch (Exception e) {
            log.warn("获取 {}.{} 的 @HttpRequestLog 注解失败", point.getSignature().getDeclaringTypeName(), point.getSignature().getName(),
                    e);
            return null;
        }
    }
}
