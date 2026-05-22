package com.github.sparkzxl.trace.config;

import com.github.sparkzxl.trace.filter.GatewayMdcFilter;
import com.github.sparkzxl.trace.filter.GatewayTraceFilter;
import com.github.sparkzxl.trace.properties.TraceProperties;
import com.github.sparkzxl.trace.subscriber.MdcPropagatingSubscriber;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Operators;

import java.util.List;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass(name = "org.springframework.cloud.gateway.filter.GlobalFilter")
class GatewayTraceConfiguration {

    private static final String HOOK_KEY = "mdc.propagation";

    private final TraceProperties traceProperties;

    GatewayTraceConfiguration(TraceProperties traceProperties) {
        this.traceProperties = traceProperties;
    }

    @PostConstruct
    public void initReactorMdcPropagation() {
        List<String> propagateHeaders = traceProperties.getPropagateHeaders();
        Hooks.onEachOperator(HOOK_KEY, Operators.lift((scannable, subscriber) ->
                new MdcPropagatingSubscriber<>(subscriber, propagateHeaders)));
    }

    @PreDestroy
    public void cleanupReactorMdcPropagation() {
        Hooks.resetOnEachOperator(HOOK_KEY);
    }

    @Bean(name = "gatewayTraceFilter")
    public GlobalFilter gatewayTraceFilter(TraceProperties traceProperties) {
        return new GatewayTraceFilter(traceProperties);
    }

    @Bean(name = "gatewayMdcFilter")
    public GlobalFilter gatewayMdcFilter() {
        return new GatewayMdcFilter();
    }
}
