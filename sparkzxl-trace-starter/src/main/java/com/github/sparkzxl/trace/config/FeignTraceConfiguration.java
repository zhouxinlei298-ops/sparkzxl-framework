package com.github.sparkzxl.trace.config;

import com.github.sparkzxl.trace.interceptor.TraceFeignRequestInterceptor;
import com.github.sparkzxl.trace.properties.TraceProperties;
import feign.RequestInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(RequestInterceptor.class)
class FeignTraceConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "traceFeignRequestInterceptor")
    public TraceFeignRequestInterceptor traceFeignRequestInterceptor(TraceProperties traceProperties) {
        return new TraceFeignRequestInterceptor(traceProperties);
    }
}
