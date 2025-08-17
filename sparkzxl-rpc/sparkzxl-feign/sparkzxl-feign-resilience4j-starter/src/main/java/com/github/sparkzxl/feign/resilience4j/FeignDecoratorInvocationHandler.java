package com.github.sparkzxl.feign.resilience4j;

import feign.InvocationHandlerFactory;
import feign.Target;
import io.github.resilience4j.feign.FeignDecorator;
import io.vavr.CheckedFunction1;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

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

    public FeignDecoratorInvocationHandler(Target<?> target,
                                           Map<Method, InvocationHandlerFactory.MethodHandler> dispatch,
                                           FeignDecorator invocationDecorator) {
        this.target = checkNotNull(target, "target");
        checkNotNull(dispatch, "dispatch");
        this.decoratedDispatch = decorateMethodHandlers(dispatch, invocationDecorator, target);
    }

    /**
     * Applies the specified {@link FeignDecorator} to all specified {@link InvocationHandlerFactory.MethodHandler}s and
     * returns the result as a map of {@link CheckedFunction1}s. Invoking a {@link CheckedFunction1}
     * will therefore invoke the decorator which, in turn, may invoke the corresponding {@link
     * InvocationHandlerFactory.MethodHandler}.
     *
     * @param dispatch            a map of the methods from the feign interface to the {@link
     *                            InvocationHandlerFactory.MethodHandler}s.
     * @param invocationDecorator the {@link FeignDecorator} with which to decorate the {@link
     *                            InvocationHandlerFactory.MethodHandler}s.
     * @param target              the target feign interface.
     * @return a new map where the {@link InvocationHandlerFactory.MethodHandler}s are decorated with the {@link
     * FeignDecorator}.
     */
    private Map<Method, CheckedFunction1<Object[], Object>> decorateMethodHandlers(
            Map<Method, InvocationHandlerFactory.MethodHandler> dispatch,
            FeignDecorator invocationDecorator, Target<?> target) {
        final Map<Method, CheckedFunction1<Object[], Object>> map = new HashMap<>();
        for (final Map.Entry<Method, InvocationHandlerFactory.MethodHandler> entry : dispatch.entrySet()) {
            final Method method = entry.getKey();
            final InvocationHandlerFactory.MethodHandler methodHandler = entry.getValue();
            if (methodHandler != null) {
                CheckedFunction1<Object[], Object> decorated = invocationDecorator
                        .decorate(methodHandler::invoke, method, methodHandler, target);
                map.put(method, decorated);
            }
        }
        return map;
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
