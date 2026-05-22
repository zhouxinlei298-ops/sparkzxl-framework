package com.github.sparkzxl.trace.subscriber;

import org.apache.commons.lang3.StringUtils;
import org.reactivestreams.Subscription;
import org.slf4j.MDC;
import reactor.core.CoreSubscriber;
import reactor.util.context.Context;

import java.util.List;

/**
 * Reactor Subscriber 装饰器，在每个信号（onSubscribe/onNext/onError/onComplete）时
 * 从 Reactor Context 恢复指定的 header 到 MDC，解决 Reactor 线程切换导致 ThreadLocal 丢失的问题。
 *
 * @author zhouxinlei
 * @since 2026-05-19 10:12
 */
public class MdcPropagatingSubscriber<T> implements CoreSubscriber<T> {

    private final CoreSubscriber<T> delegate;
    private final List<String> propagateHeaders;

    public MdcPropagatingSubscriber(CoreSubscriber<T> delegate, List<String> propagateHeaders) {
        this.delegate = delegate;
        this.propagateHeaders = propagateHeaders;
    }

    @Override
    public void onSubscribe(Subscription s) {
        propagateMdc();
        delegate.onSubscribe(s);
    }

    @Override
    public void onNext(T t) {
        propagateMdc();
        delegate.onNext(t);
    }

    @Override
    public void onError(Throwable t) {
        propagateMdc();
        delegate.onError(t);
    }

    @Override
    public void onComplete() {
        propagateMdc();
        delegate.onComplete();
    }

    @Override
    public Context currentContext() {
        return delegate.currentContext();
    }

    private void propagateMdc() {
        Context ctx = delegate.currentContext();
        for (String header : propagateHeaders) {
            String value = ctx.getOrDefault(header, "");
            if (StringUtils.isNotEmpty(value)) {
                MDC.put(header, value);
            }
        }
    }
}
