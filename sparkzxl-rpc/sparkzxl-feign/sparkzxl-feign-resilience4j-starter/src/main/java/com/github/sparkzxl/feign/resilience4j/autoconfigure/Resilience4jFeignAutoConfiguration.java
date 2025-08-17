package com.github.sparkzxl.feign.resilience4j.autoconfigure;

import feign.Feign;
import feign.RequestInterceptor;
import io.github.resilience4j.feign.FeignDecorators;
import io.github.resilience4j.feign.Resilience4jFeign;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.vavr.control.Try;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.Environment;

import java.util.List;

/**
 * description:
 *
 * @author zhouxinlei
 * @since 2025-08-17 11:19:29
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({Feign.class})
@ConditionalOnProperty(name = "feign.resilience4j.enabled", havingValue = "true", matchIfMissing = true)
public class Resilience4jFeignAutoConfiguration {


    @Bean
    public FeignDecorators.Builder defaultBuilder(Environment environment,
                                                  RetryRegistry retryRegistry) {
        String name = environment.getProperty("feign.client.name");
        if (StringUtils.isEmpty(name)) {
            RetryConfig defaultConfig = retryRegistry.getDefaultConfig();
            Retry retry = Retry.of("defaultClient",
                    RetryConfig.from(defaultConfig).retryOnException(throwable -> throwable instanceof feign.RetryableException)
                            .build());
            return FeignDecorators.builder().withRetry(retry);
        }
        Retry retry = Try.of(() -> retryRegistry.retry(name, name)).getOrElseGet(throwable -> retryRegistry.retry(name));
        //覆盖其中的异常判断，只针对 feign.RetryableException 进行重试，所有需要重试的异常我们都在 DefaultErrorDecoder 以及 Resilience4jFeignClient 中封装成了 RetryableException
        retry = Retry.of(name,
                RetryConfig.from(retry.getRetryConfig()).retryOnException(throwable -> throwable instanceof feign.RetryableException)
                        .build());
        return FeignDecorators.builder().withRetry(retry);
    }

    @Bean
    @Scope("prototype")
    @ConditionalOnMissingBean
    public Feign.Builder feignResilience4jBuilder(FeignDecorators.Builder builder,
                                                  List<RequestInterceptor> requestInterceptorList) {
        return Resilience4jFeign.builder(builder)
                .requestInterceptors(requestInterceptorList)
                .decode404();
    }
}
