package com.github.sparkzxl.feign.resilience4j.autoconfigure;

import feign.Feign;
import feign.RequestInterceptor;
import io.github.resilience4j.feign.FeignDecorators;
import io.github.resilience4j.feign.Resilience4jFeign;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.vavr.control.Try;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.function.Function;

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
        assert name != null;
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
                                                  List<RequestInterceptor> requestInterceptorList,
                                                  // 注入 Feign 客户端对应的 fallback 实例（通过泛型匹配具体类型）
                                                  ObjectProvider<?> fallbackObjectProvider,
                                                  ObjectProvider<FallbackFactory<?>> factoryObjectProvider) {
        fallbackObjectProvider.ifAvailable(fallback -> {
            // 将 fallback 实例添加到 Resilience4j 装饰器
            // 第二个参数：触发降级的异常类型（通常为 Exception 表示所有异常）
            builder.withFallback(fallback, Exception.class);
        });
        // 3. 注册降级工厂（用于为每个 Feign 客户端提供降级实现）
        factoryObjectProvider.ifAvailable(fallbackFactory -> {
            Function<Exception, ?> function = fallbackFactory::create;
            builder.withFallbackFactory(function);
        });
        return Resilience4jFeign.builder(builder.build())
                .requestInterceptors(requestInterceptorList)
                .decode404();
    }
}
