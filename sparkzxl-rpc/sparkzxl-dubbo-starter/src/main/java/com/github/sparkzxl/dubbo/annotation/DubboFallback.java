package com.github.sparkzxl.dubbo.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * Dubbo服务降级注解
 * <p>
 * 用于声明式注册Dubbo服务降级处理器。
 * 支持类级别和方法级别注解：
 * </p>
 *
 * @author zhouxinlei
 * @since 2026-03-20
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface DubboFallback {

    /**
     * 客户端/服务名称（必填）
     * <p>
     * 用于标识服务名称，方便日志输出和问题排查。
     * </p>
     *
     * @return 客户端/服务名称，不能为空
     */
    String value();

    /**
     * 服务接口类（必填）
     * <p>必须是 Dubbo 服务接口类型，用于匹配降级处理器</p>
     *
     * @return 服务接口类
     */
    Class<?> interfaceClass();
}
