package com.github.sparkzxl.feign.resilience4j.autoconfigure;

import com.github.sparkzxl.feign.resilience4j.Resilience4jFeign;
import feign.Feign;
import feign.RequestInterceptor;
import feign.Target;
import feign.codec.Encoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.openfeign.CircuitBreakerNameResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.lang.reflect.Method;
import java.util.List;

/**
 * description:
 *
 * @author zhouxinlei
 * @since 2025-08-18 10:03:30
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({Feign.class, CircuitBreaker.class})
@ConditionalOnProperty(name = "feign.circuitbreaker.enabled", havingValue = "true", matchIfMissing = true)
public class Resilience4jFeignClientConfiguration {

    @Bean
    @ConditionalOnMissingBean(CircuitBreakerNameResolver.class)
    @ConditionalOnProperty(value = "feign.circuitbreaker.alphanumeric-ids.enabled", havingValue = "false",
            matchIfMissing = true)
    public CircuitBreakerNameResolver circuitBreakerNameResolver() {
        return (String feignClientName, Target<?> target, Method method) -> feignClientName + "_" + method.getName();
    }

    @Bean
    @ConditionalOnMissingBean(CircuitBreakerNameResolver.class)
    @ConditionalOnProperty(value = "feign.circuitbreaker.alphanumeric-ids.enabled", havingValue = "true")
    public CircuitBreakerNameResolver alphanumericCircuitBreakerNameResolver() {
        return new AlphanumericCircuitBreakerNameResolver();
    }

    @Bean
    @Scope("prototype")
    @ConditionalOnMissingBean
    public Feign.Builder feignResilience4jBuilder(CircuitBreakerFactory circuitBreakerFactory,
                                                  @Value("${feign.circuitbreaker.group.enabled:false}") boolean circuitBreakerGroupEnabled,
                                                  CircuitBreakerNameResolver circuitBreakerNameResolver,
                                                  List<RequestInterceptor> requestInterceptorList,
                                                  Encoder encoder) {
        return Resilience4jFeign.builder(circuitBreakerFactory, circuitBreakerGroupEnabled, circuitBreakerNameResolver)
                .requestInterceptors(requestInterceptorList)
                .encoder(encoder);
    }

    static class DefaultCircuitBreakerNameResolver implements CircuitBreakerNameResolver {

        @Override
        public String resolveCircuitBreakerName(String feignClientName, Target<?> target, Method method) {
            return Feign.configKey(target.type(), method);
        }

    }

    static class AlphanumericCircuitBreakerNameResolver extends DefaultCircuitBreakerNameResolver {

        @Override
        public String resolveCircuitBreakerName(String feignClientName, Target<?> target, Method method) {
            return super.resolveCircuitBreakerName(feignClientName, target, method).replaceAll("[^a-zA-Z0-9]", "");
        }

    }

}
