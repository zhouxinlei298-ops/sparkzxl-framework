package com.github.sparkzxl.trace.config;

import com.github.sparkzxl.trace.filter.GatewayMdcFilter;
import com.github.sparkzxl.trace.filter.GatewayTraceFilter;
import com.github.sparkzxl.trace.properties.TraceProperties;
import com.github.sparkzxl.trace.provider.LocalReactiveTraceIdProvider;
import com.github.sparkzxl.trace.provider.ReactiveTraceIdProvider;
import com.github.sparkzxl.trace.provider.SkyWalkingReactiveTraceIdProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass(name = "org.springframework.cloud.gateway.filter.GlobalFilter")
class GatewayTraceConfiguration {

    @Bean
    @ConditionalOnMissingBean(ReactiveTraceIdProvider.class)
    @ConditionalOnProperty(name = "sparkzxl.trace.type", havingValue = "skywalking")
    public ReactiveTraceIdProvider skyWalkingReactiveTraceIdProvider() {
        return new SkyWalkingReactiveTraceIdProvider();
    }

    @Bean
    @ConditionalOnMissingBean(ReactiveTraceIdProvider.class)
    @ConditionalOnProperty(name = "sparkzxl.trace.type", havingValue = "local", matchIfMissing = true)
    public ReactiveTraceIdProvider localReactiveTraceIdProvider() {
        return new LocalReactiveTraceIdProvider();
    }

    @Bean(name = "gatewayTraceFilter")
    public GlobalFilter gatewayTraceFilter(ReactiveTraceIdProvider reactiveTraceIdProvider,
                                           TraceProperties traceProperties) {
        return new GatewayTraceFilter(reactiveTraceIdProvider, traceProperties);
    }

    @Bean(name = "gatewayMdcFilter")
    public GlobalFilter gatewayMdcFilter() {
        return new GatewayMdcFilter();
    }
}
