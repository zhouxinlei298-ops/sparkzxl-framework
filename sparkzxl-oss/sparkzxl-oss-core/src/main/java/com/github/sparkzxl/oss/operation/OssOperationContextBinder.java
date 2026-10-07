package com.github.sparkzxl.oss.operation;

import com.github.sparkzxl.oss.executor.OssExecutorFactoryContext;

/**
 * 管理当前线程操作域的绑定与恢复；不传播到异步线程。
 *
 * @author zhouxinlei
 * @see OssOperationContextManager
 * @since 2026-10-06
 */
final class OssOperationContextBinder {

    private static final ThreadLocal<OssOperationContextScope> CURRENT = new ThreadLocal<>();

    private OssOperationContextBinder() {
    }

    static OssOperationContextScope current() {
        return CURRENT.get();
    }

    static OssOperationContextScope bind(OssOperationHandle handle, boolean owns, OssExecutorFactoryContext factory) {
        OssOperationContextScope scope =
                new OssOperationContextScope(CURRENT.get(), handle, owns, factory);
        CURRENT.set(scope);
        return scope;
    }

    static void restore(OssOperationContextScope scope) {
        if (CURRENT.get() != scope) {
            throw new IllegalStateException("OSS scopes must close in LIFO order");
        }
        if (scope.previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(scope.previous);
        }
    }
}
