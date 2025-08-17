package com.github.sparkzxl.feign.resilience4j.autoconfigure;

import feign.Feign;
import feign.RequestInterceptor;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.feign.Resilience4jFeign;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.util.List;

/**
 * description: Resilience4jFeignAutoConfiguration
 *
 * @author zhouxinlei
 * @since 2025-08-17 11:19:29
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({Feign.class})
@ConditionalOnProperty(name = "feign.resilience4j.enabled", havingValue = "true", matchIfMissing = true)
public class Resilience4jFeignAutoConfiguration {

    @Bean
    @Scope("prototype")
    @ConditionalOnMissingBean
    public Feign.Builder feignResilience4jBuilder(CircuitBreakerRegistry circuitBreakerRegistry,
                                                  List<RequestInterceptor> requestInterceptorList) {
        return Resilience4jFeign.builder(circuitBreakerRegistry)
                .requestInterceptors(requestInterceptorList)
                .decode404();
    }
}
