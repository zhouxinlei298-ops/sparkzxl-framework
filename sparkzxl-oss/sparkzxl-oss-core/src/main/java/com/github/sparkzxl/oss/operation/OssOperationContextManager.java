package com.github.sparkzxl.oss.operation;

import com.github.sparkzxl.oss.executor.OssExecutorFactoryContext;

/**
 * 建立显式操作域，并拒绝缺少目标的访问。
 *
 * @author zhouxinlei
 * @since 2026-10-06
 * @see java.lang.AutoCloseable
 */
public final class OssOperationContextManager {

    private OssOperationContextManager() {}

    /**
     * 建立显式操作域；同工厂同目标嵌套复用在途句柄。
     *
     * @param factory 客户端代际工厂
     * @param clientId 固定客户端标识
     * @return 持有固定配置与客户端代际的可关闭操作域
     */
    public static OssOperationContextScope open(
            OssExecutorFactoryContext factory, String clientId) {
        OssOperationContextScope current = OssOperationContextBinder.current();
        if (current != null) {
            if (current.factory != factory
                    || !current.handle.getContext().getClientId().equals(clientId)) {
                throw new IllegalStateException(
                        "Cannot replace an active OSS target; open an independent operation");
            }
            return OssOperationContextBinder.bind(current.handle, false, factory);
        }
        return openIndependent(factory, clientId);
    }

    /**
     * 独立解析目标并建立操作域，供异步任务显式使用。
     *
     * @param factory 客户端代际工厂
     * @param clientId 固定客户端标识
     * @return 独立解析目标的可关闭操作域
     */
    public static OssOperationContextScope openIndependent(
            OssExecutorFactoryContext factory, String clientId) {
        return OssOperationContextBinder.bind(factory.open(clientId), true, factory);
    }

    /**
     * 获取当前域的句柄，缺少操作域时拒绝访问。
     *
     * @return 当前线程绑定的有效操作句柄
     */
    public static OssOperationHandle requiredHandle() {
        OssOperationContextScope scope = OssOperationContextBinder.current();
        if (scope == null) {
            throw new IllegalStateException("An explicit OSS operation scope is required");
        }
        scope.handle.assertOpen();
        return scope.handle;
    }
}
