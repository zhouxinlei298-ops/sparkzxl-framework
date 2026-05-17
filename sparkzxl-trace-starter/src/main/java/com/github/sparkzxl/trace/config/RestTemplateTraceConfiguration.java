package com.github.sparkzxl.trace.config;

import com.github.sparkzxl.trace.interceptor.TraceRestTemplateHeaderInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@ConditionalOnClass(RestTemplate.class)
class RestTemplateTraceConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TraceRestTemplateHeaderInterceptor traceRestTemplateHeaderInterceptor() {
        return new TraceRestTemplateHeaderInterceptor();
    }
}
