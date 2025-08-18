package com.github.sparkzxl.feign.resilience4j;

import feign.Feign;
import feign.InvocationHandlerFactory;
import feign.Target;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.openfeign.CircuitBreakerNameResolver;
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

/**
 * description: 重写Resilience4jFeign，支持FeignClient注解的降级类,同时支持熔断
 *
 * @author zhouxinlei
 * @since 2025-08-17 15:13:44
 */
@SuppressWarnings(value = "all")
public class Resilience4jFeign {

    // 构建器入口：传入熔断器注册中心
    public static Builder builder(CircuitBreakerFactory circuitBreakerFactory, boolean circuitBreakerGroupEnabled, CircuitBreakerNameResolver circuitBreakerNameResolver) {
        return new Builder(circuitBreakerFactory, circuitBreakerGroupEnabled, circuitBreakerNameResolver);
    }

    public static final class Builder extends Feign.Builder implements ApplicationContextAware {

        private final CircuitBreakerFactory circuitBreakerFactory; // 熔断器注册中心
        private final boolean circuitBreakerGroupEnabled;
        private final CircuitBreakerNameResolver circuitBreakerNameResolver;
        private ApplicationContext applicationContext; // Spring上下文
        private FeignContext feignContext; // Feign客户端专属上下文

        // 构造器：初始化默认熔断配置
        public Builder(CircuitBreakerFactory circuitBreakerFactory, boolean circuitBreakerGroupEnabled, CircuitBreakerNameResolver circuitBreakerNameResolver) {
            this.circuitBreakerFactory = circuitBreakerFactory;
            this.circuitBreakerGroupEnabled = circuitBreakerGroupEnabled;
            this.circuitBreakerNameResolver = circuitBreakerNameResolver;
        }

        // 禁止自定义调用处理器工厂（强制使用当前类的熔断逻辑）
        @Override
        public Feign.Builder invocationHandlerFactory(InvocationHandlerFactory invocationHandlerFactory) {
            throw new UnsupportedOperationException("不支持自定义InvocationHandlerFactory，需使用内置熔断逻辑");
        }

        // 构建Feign实例：核心逻辑是创建方法级熔断的调用处理器
        @Override
        public Feign build() {
            super.invocationHandlerFactory(new InvocationHandlerFactory() {
                @Override
                public InvocationHandler create(Target target, Map<Method, MethodHandler> dispatch) {
                    // 1. 从Spring上下文获取FeignClient的配置信息（fallback、fallbackFactory等）
                    GenericApplicationContext genericApplicationContext = (GenericApplicationContext) applicationContext;
                    BeanDefinition beanDefinition = genericApplicationContext.getBeanDefinition(target.type().getName());
                    FeignClientFactoryBean feignClientFactoryBean = (FeignClientFactoryBean) beanDefinition.getAttribute("feignClientsRegistrarFactoryBean");

                    // 2. 提取FeignClient的唯一标识（contextId/服务名）和降级配置
                    String feignClientName = StringUtils.hasText(feignClientFactoryBean.getContextId())
                            ? feignClientFactoryBean.getContextId()
                            : feignClientFactoryBean.getName();
                    Class<?> fallbackClass = feignClientFactoryBean.getFallback();
                    Class<?> fallbackFactoryClass = feignClientFactoryBean.getFallbackFactory();

                    // 3. 解析降级实例或降级工厂
                    FallbackFactory<?> fallbackFactory = resolveFallbackFactory(feignClientName, fallbackClass, fallbackFactoryClass, target.type());

                    // 4. 创建方法级熔断的调用处理器
                    return new FeignCircuitBreakerInvocationHandler(
                            circuitBreakerFactory, feignClientName, target, dispatch, fallbackFactory,
                            circuitBreakerGroupEnabled, circuitBreakerNameResolver);
                }
            });
            return super.build();
        }

        /**
         * 解析fallback或fallbackFactory，优先使用fallbackFactory
         *
         * @param feignClientName      feign客户端名称
         * @param fallbackClass        降级类
         * @param fallbackFactoryClass 降级工厂类
         * @param targetType           目标类型
         * @return FallbackFactory<?>
         */
        private FallbackFactory<?> resolveFallbackFactory(String feignClientName, Class<?> fallbackClass, Class<?> fallbackFactoryClass, Class<?> targetType) {
            // 若配置了fallbackFactory，优先使用
            if (fallbackFactoryClass != void.class) {
                return (FallbackFactory<?>) getFromContext(feignClientName, "fallbackFactory", fallbackFactoryClass, FallbackFactory.class);
            }
            // 若配置了fallback，包装为默认FallbackFactory
            if (fallbackClass != void.class) {
                Object fallbackInstance = getFromContext(feignClientName, "fallback", fallbackClass, targetType);
                return new FallbackFactory.Default<>(fallbackInstance);
            }
            // 未配置降级，返回null（不启用降级）
            return null;
        }

        private <T> T getFromContext(String name, String type,
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
            return (T) fallbackInstance;
        }

        // 注入Spring上下文
        @Override
        public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
            this.applicationContext = applicationContext;
            this.feignContext = applicationContext.getBean(FeignContext.class);
        }
    }

}
