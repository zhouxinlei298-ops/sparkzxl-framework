package com.github.sparkzxl.dubbo.config;

import com.github.sparkzxl.dubbo.fallback.DefaultDubboFallbackHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Dubbo服务降级自动配置
 * <p>
 * 启用Dubbo服务降级扩展功能，包括：
 * <ul>
 *   <li>默认降级处理器</li>
 *   <li>降级注册中心</li>
 *   <li>注解式降级处理器注册</li>
 * </ul>
 * </p>
 *
 * @author zhouxinlei
 * @since 2026-03-20
 */
@Slf4j
@Configuration
public class DubboFallbackAutoConfiguration {

    public DubboFallbackAutoConfiguration() {
        log.info("[Dubbo降级] Dubbo自定义降级扩展已启用，默认降级处理器: {}", DefaultDubboFallbackHandler.INSTANCE.getClass().getName());
    }
}
