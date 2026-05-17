package com.github.sparkzxl.trace.config;

import com.github.sparkzxl.core.trace.TraceIdContext;
import com.github.sparkzxl.trace.provider.LocalTraceIdContext;
import com.github.sparkzxl.trace.provider.SkywalkingTraceIdContext;
import com.github.sparkzxl.trace.properties.TraceProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@EnableConfigurationProperties(TraceProperties.class)
@ConditionalOnProperty(name = "sparkzxl.trace.enabled", havingValue = "true", matchIfMissing = true)
@Import({
        ServletTraceConfiguration.class,
        GatewayTraceConfiguration.class,
        RestTemplateTraceConfiguration.class,
        FeignTraceConfiguration.class
})
public class TraceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TraceIdContext.class)
    @ConditionalOnProperty(name = "sparkzxl.trace.type", havingValue = "skywalking")
    public TraceIdContext skywalkingTraceIdContext() {
        return new SkywalkingTraceIdContext();
    }

    @Bean
    @ConditionalOnMissingBean(TraceIdContext.class)
    @ConditionalOnProperty(name = "sparkzxl.trace.type", havingValue = "local", matchIfMissing = true)
    public TraceIdContext localTraceIdContext() {
        return new LocalTraceIdContext();
    }
}
