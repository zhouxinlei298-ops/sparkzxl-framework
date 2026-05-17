package com.github.sparkzxl.trace.config;

import com.github.sparkzxl.trace.filter.GatewayMdcFilter;
import com.github.sparkzxl.trace.filter.GatewayTraceFilter;
import com.github.sparkzxl.trace.properties.TraceProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass(name = "org.springframework.cloud.gateway.filter.GlobalFilter")
class GatewayTraceConfiguration {

    @Bean(name = "gatewayTraceFilter")
    public GlobalFilter gatewayTraceFilter(TraceProperties traceProperties) {
        return new GatewayTraceFilter(traceProperties);
    }

    @Bean(name = "gatewayMdcFilter")
    public GlobalFilter gatewayMdcFilter() {
        return new GatewayMdcFilter();
    }
}
