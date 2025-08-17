package com.github.sparkzxl.feign.resilience4j;

import feign.Feign;
import feign.InvocationHandlerFactory;
import feign.Target;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.feign.FeignDecorators;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClientFactoryBean;
import org.springframework.cloud.openfeign.FeignContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.util.StringUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * description: 重写Resilience4jFeign，支持FeignClient注解的降级类,同时支持熔断
 *
 * @author zhouxinlei
 * @since 2025-08-17 15:13:44
 */
@SuppressWarnings(value = "all")
public class Resilience4jFeign {

    public static Builder builder(CircuitBreakerRegistry circuitBreakerRegistry) {
        return new Builder(circuitBreakerRegistry);
    }

    public static final class Builder extends Feign.Builder
            implements ApplicationContextAware {

        private final CircuitBreakerRegistry circuitBreakerRegistry;
        private final FeignDecorators.Builder decoratorsBuilder;
        private final ConcurrentHashMap<String, CircuitBreakerConfig> circuitBreakerConfigConfigs = new ConcurrentHashMap<>();
        private final Function<String, CircuitBreakerConfig> defaultCircuitBreakerConfig;

        private ApplicationContext applicationContext;

        private FeignContext feignContext;

        public Builder(CircuitBreakerRegistry circuitBreakerRegistry) {
            this.circuitBreakerRegistry = circuitBreakerRegistry;
            this.defaultCircuitBreakerConfig = id -> circuitBreakerRegistry.getDefaultConfig();
            this.decoratorsBuilder = FeignDecorators.builder();
        }

        @Override
        public Feign.Builder invocationHandlerFactory(
                InvocationHandlerFactory invocationHandlerFactory) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Feign build() {
            super.invocationHandlerFactory(new InvocationHandlerFactory() {

                @Override
                public InvocationHandler create(Target target,
                                                Map<Method, MethodHandler> dispatch) {

                    GenericApplicationContext genericApplicationContext = (GenericApplicationContext) Builder.this.applicationContext;
                    BeanDefinition beanDefinition = genericApplicationContext.getBeanDefinition(target.type().getName());

                    FeignClientFactoryBean feignClientFactoryBean = (FeignClientFactoryBean) beanDefinition.getAttribute("feignClientsRegistrarFactoryBean");

                    Class fallback = feignClientFactoryBean.getFallback();
                    Class fallbackFactory = feignClientFactoryBean.getFallbackFactory();
                    String beanName = feignClientFactoryBean.getContextId();

                    if (!StringUtils.hasText(beanName)) {
                        beanName = feignClientFactoryBean.getName();
                    }
                    CircuitBreakerConfig circuitBreakerConfig = circuitBreakerConfigConfigs.computeIfAbsent(beanName, defaultCircuitBreakerConfig);
                    CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(beanName, circuitBreakerConfig);
                    decoratorsBuilder.withCircuitBreaker(circuitBreaker);
                    Object fallbackInstance;
                    FallbackFactory fallbackFactoryInstance;
                    // check fallback and fallbackFactory properties
                    if (void.class != fallback) {
                        fallbackInstance = getFromContext(beanName, "fallback", fallback,
                                target.type());
                        decoratorsBuilder.withFallback(fallbackInstance);
                    }
                    if (void.class != fallbackFactory) {
                        fallbackFactoryInstance = (FallbackFactory) getFromContext(
                                beanName, "fallbackFactory", fallbackFactory,
                                FallbackFactory.class);
                        Function<Exception, ?> function = fallbackFactoryInstance::create;
                        decoratorsBuilder.withFallbackFactory(function);
                    }
                    FeignDecorators invocationDecorator = decoratorsBuilder.build();
                    return new FeignDecoratorInvocationHandler(target, dispatch, invocationDecorator);
                }

                private Object getFromContext(String name, String type,
                                              Class fallbackType, Class targetType) {
                    Object fallbackInstance = feignContext.getInstance(name,
                            fallbackType);
                    if (fallbackInstance == null) {
                        throw new IllegalStateException(String.format(
                                "No %s instance of type %s found for feign client %s",
                                type, fallbackType, name));
                    }

                    if (!targetType.isAssignableFrom(fallbackType)) {
                        throw new IllegalStateException(String.format(
                                "Incompatible %s instance. Fallback/fallbackFactory of type %s is not assignable to %s for feign client %s",
                                type, fallbackType, targetType, name));
                    }
                    return fallbackInstance;
                }
            });
            return super.build();
        }

        @Override
        public void setApplicationContext(ApplicationContext applicationContext)
                throws BeansException {
            this.applicationContext = applicationContext;
            feignContext = this.applicationContext.getBean(FeignContext.class);
        }

    }

}
