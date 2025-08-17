package com.github.sparkzxl.feign.resilience4j;

import feign.Feign;
import feign.InvocationHandlerFactory;
import feign.Target;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.feign.FeignDecorator;
import io.github.resilience4j.feign.FeignDecorators;
import io.vavr.CheckedFunction1;
import org.springframework.cloud.openfeign.FallbackFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static feign.Util.checkNotNull;

/**
 * description: resilience4j 重写DecoratorInvocationHandler
 *
 * @author zhouxinlei
 * @see io.github.resilience4j.feign.DecoratorInvocationHandler
 * @since 2025-08-17 17:00:34
 */
public class FeignDecoratorInvocationHandler implements InvocationHandler {

    private final Target<?> target;
    private final Map<Method, CheckedFunction1<Object[], Object>> decoratedDispatch;
    private final Function<String, CircuitBreakerConfig> defaultCircuitBreakerConfig;
    private final String feignClientName;


    public FeignDecoratorInvocationHandler(Target<?> target,
                                           Map<Method, InvocationHandlerFactory.MethodHandler> dispatch,
                                           CircuitBreakerRegistry circuitBreakerRegistry,
                                           FallbackFactory<?> fallbackFactoryInstance,
                                           String feignClientName) {
        this.target = checkNotNull(target, "target");
        checkNotNull(dispatch, "dispatch");
        this.defaultCircuitBreakerConfig = id -> circuitBreakerRegistry.getDefaultConfig();
        this.feignClientName = feignClientName;
        this.decoratedDispatch = decorateMethodHandlers(dispatch, target, circuitBreakerRegistry, fallbackFactoryInstance);
    }

    /**
     * Applies the specified {@link FeignDecorator} to all specified {@link InvocationHandlerFactory.MethodHandler}s and
     * returns the result as a map of {@link CheckedFunction1}s. Invoking a {@link CheckedFunction1}
     * will therefore invoke the decorator which, in turn, may invoke the corresponding {@link
     * InvocationHandlerFactory.MethodHandler}.
     *
     * @param dispatch                a map of the methods from the feign interface to the {@link
     *                                InvocationHandlerFactory.MethodHandler}s.
     * @param target                  the target feign interface.
     * @param circuitBreakerRegistry  断路器注册实例
     * @param fallbackFactoryInstance 降级工厂实例
     * @return a new map where the {@link InvocationHandlerFactory.MethodHandler}s are decorated with the {@link
     * FeignDecorator}.
     */
    private Map<Method, CheckedFunction1<Object[], Object>> decorateMethodHandlers(
            Map<Method, InvocationHandlerFactory.MethodHandler> dispatch,
            Target<?> target,
            CircuitBreakerRegistry circuitBreakerRegistry,
            FallbackFactory<?> fallbackFactoryInstance) {
        final Map<Method, CheckedFunction1<Object[], Object>> map = new HashMap<>();
        for (final Map.Entry<Method, InvocationHandlerFactory.MethodHandler> entry : dispatch.entrySet()) {
            final Method method = entry.getKey();
            final InvocationHandlerFactory.MethodHandler methodHandler = entry.getValue();
            if (methodHandler != null) {
                // 1. 生成方法唯一标识（接口全类名+方法名+参数类型）
                String circuitName = generateMethodId(target, method);
                // 2. 获取当前方法的熔断配置（优先用客户端配置，否则用默认）
                CircuitBreakerConfig circuitBreakerConfig = getMethodCircuitBreakerConfig(feignClientName);
                // 3. 获取/创建方法级别的CircuitBreaker
                CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(circuitName, circuitBreakerConfig);
                // 4. 构建 FeignDecorators
                FeignDecorators invocationDecorator = buildMethodDecorators(circuitBreaker, fallbackFactoryInstance);
                CheckedFunction1<Object[], Object> decorated = invocationDecorator
                        .decorate(methodHandler::invoke, method, methodHandler, target);
                map.put(method, decorated);
            }
        }
        return map;
    }


    /**
     * 构建当前方法的熔断装饰器（绑定熔断+降级）
     */
    private FeignDecorators buildMethodDecorators(CircuitBreaker circuitBreaker,
                                                  FallbackFactory<?> fallbackFactoryInstance) {
        FeignDecorators.Builder decoratorBuilder = FeignDecorators.builder();
        decoratorBuilder.withCircuitBreaker(circuitBreaker);
        // 绑定降级工厂（FeignClient注解配置的fallbackFactory）
        if (fallbackFactoryInstance != null) {
            Function<Exception, ?> fallbackFunction = e -> {
                Object fallback = fallbackFactoryInstance.create(e);
                if (!target.type().isInstance(fallback)) {
                    throw new IllegalArgumentException("Fallback instance does not implement target interface: " + target.type());
                }
                return fallback;
            };
            decoratorBuilder.withFallbackFactory(fallbackFunction);
        }
        return decoratorBuilder.build();
    }

    /**
     * 生成方法唯一ID
     *
     * @param target target
     * @param method 方法
     * @return String
     */
    private String generateMethodId(Target<?> target, Method method) {
        return Feign.configKey(target.type(), method);
    }

    /**
     * 获取方法级别的熔断配置（此处复用客户端配置，可扩展为方法级配置）
     */
    private CircuitBreakerConfig getMethodCircuitBreakerConfig(String feignClientName) {
        // 如需方法级配置，可在此处根据methodId从配置文件加载
        return defaultCircuitBreakerConfig.apply(feignClientName);
    }

    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args)
            throws Throwable {
        switch (method.getName()) {
            case "equals":
                return equals(args.length > 0 ? args[0] : null);

            case "hashCode":
                return hashCode();

            case "toString":
                return toString();

            default:
                break;
        }

        return decoratedDispatch.get(method).apply(args);
    }

    @Override
    public boolean equals(Object obj) {
        Object compareTo = obj;
        if (compareTo == null) {
            return false;
        }
        if (Proxy.isProxyClass(compareTo.getClass())) {
            compareTo = Proxy.getInvocationHandler(compareTo);
        }
        if (compareTo instanceof FeignDecoratorInvocationHandler) {
            final FeignDecoratorInvocationHandler other = (FeignDecoratorInvocationHandler) compareTo;
            return target.equals(other.target);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return target.hashCode();
    }

    @Override
    public String toString() {
        return target.toString();
    }
}
