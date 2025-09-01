package com.github.sparkzxl.boot.config;

import com.github.sparkzxl.boot.properties.TraceProperties;
import com.github.sparkzxl.core.context.LocalTraceIdContext;
import com.github.sparkzxl.core.context.SkywalkingTraceIdContext;
import com.github.sparkzxl.core.context.TraceIdContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * description: 链路追踪自动配置
 *
 * @author zhouxinlei
 * @since 2025-08-30 13:49:52
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(TraceProperties.class)
@ConditionalOnProperty(name = "spring.trace.enabled", havingValue = "true", matchIfMissing = true)
public class TraceAutoConfig {

    @Bean
    @ConditionalOnProperty(name = "spring.trace.type", havingValue = "skywalking")
    public TraceIdContext traceIdContext() {
        return new SkywalkingTraceIdContext();
    }

    @Bean
    @ConditionalOnProperty(name = "spring.trace.type", havingValue = "local")
    public TraceIdContext localTraceIdContext() {
        return new LocalTraceIdContext();
    }
}
