package com.github.sparkzxl.oss.operation;

import com.github.sparkzxl.oss.executor.OssExecutorFactoryContext;

/**
 * 按后进先出顺序关闭操作域并恢复外层上下文。
 *
 * @author zhouxinlei
 * @since 2026-10-06
 * @see OssOperationContextManager
 */
public final class OssOperationContextScope implements AutoCloseable {

    final OssOperationContextScope previous;

    final OssOperationHandle handle;

    final OssExecutorFactoryContext factory;

    private final boolean ownsHandle;

    private final Thread owner = Thread.currentThread();

    private boolean closed;

    OssOperationContextScope(
            OssOperationContextScope previous,
            OssOperationHandle handle,
            boolean ownsHandle,
            OssExecutorFactoryContext factory) {
        this.previous = previous;
        this.handle = handle;
        this.ownsHandle = ownsHandle;
        this.factory = factory;
    }

    /**
     * 获取当前操作句柄
     *
     * @return 当前操作句柄
     */
    public OssOperationHandle getHandle() {
        handle.assertOpen();
        return handle;
    }

    /** 关闭当前资源并释放持有的引用。 */
    @Override
    public void close() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("OSS scope cannot cross threads");
        }
        if (closed) {
            return;
        }
        OssOperationContextBinder.restore(this);
        closed = true;
        if (ownsHandle) {
            handle.close();
        }
    }
}
