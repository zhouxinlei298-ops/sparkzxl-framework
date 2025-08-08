package com.github.sparkzxl.feign.resilience4j.autoconfigure;

import feign.Feign;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.openfeign.FeignCircuitBreaker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.util.List;


/**
 * description:
 *
 * @author zhouxinlei
 * @since 2025-08-02 14:37:00
 */
@ConditionalOnProperty("feign.circuitbreaker.enabled")
@Configuration
public class Resilience4jFeignConfiguration {

    @Bean
    @Scope("prototype")
    @ConditionalOnBean(CircuitBreakerFactory.class)
    public Feign.Builder circuitBreakerFeignBuilder(@Autowired List<RequestInterceptor> requestInterceptorList) {
        return FeignCircuitBreaker.builder()
                .requestInterceptors(requestInterceptorList);
    }

}
