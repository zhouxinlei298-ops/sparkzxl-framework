package com.github.sparkzxl.core.context;

import org.springframework.lang.Nullable;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * 请求上下文工具类：统一管理 RequestAttributes 的获取和跨线程传播
 *
 * <p>提供两类获取方法：
 * <ul>
 *   <li>{@code get...OrNull()} — 安全获取，无上下文时返回 null</li>
 *   <li>{@code current...()} — 标准获取，无上下文时抛 IllegalStateException</li>
 * </ul>
 *
 * <p>跨线程传播：
 * <ul>
 *   <li>{@link #enableInheritable()} — 开启 InheritableThreadLocal 继承，适用于 new Thread() 场景</li>
 *   <li>{@link #capture()} / {@link #restore} / {@link #reset()} — 手动快照模式，适用于线程池场景</li>
 *   <li>{@link #wrapRunnable} / {@link #wrapCallable} — 一键包装，自动传播和清理</li>
 * </ul>
 *
 * @author zhouxinlei
 */
public class RequestContextHelper {

    private RequestContextHelper() {
    }

    // ==================== 安全获取（返回 null，不抛异常） ====================

    /**
     * 获取当前 RequestAttributes，无上下文时返回 null
     */
    @Nullable
    public static RequestAttributes getRequestAttributesOrNull() {
        return RequestContextHolder.getRequestAttributes();
    }

    /**
     * 获取当前 ServletRequestAttributes，无上下文时返回 null
     */
    @Nullable
    public static ServletRequestAttributes getServletRequestAttributesOrNull() {
        RequestAttributes attrs = getRequestAttributesOrNull();
        return attrs instanceof ServletRequestAttributes ? (ServletRequestAttributes) attrs : null;
    }

    /**
     * 获取当前 HttpServletRequest，无上下文时返回 null。
     * 适用于可能不在 Web 请求线程中执行的场景（如 Feign 拦截器、异步任务）。
     */
    @Nullable
    public static HttpServletRequest getHttpServletRequestOrNull() {
        ServletRequestAttributes attrs = getServletRequestAttributesOrNull();
        return attrs != null ? attrs.getRequest() : null;
    }

    /**
     * 获取当前 HttpServletResponse，无上下文时返回 null
     */
    @Nullable
    public static HttpServletResponse getHttpServletResponseOrNull() {
        ServletRequestAttributes attrs = getServletRequestAttributesOrNull();
        return attrs != null ? attrs.getResponse() : null;
    }

    // ==================== 标准获取（无上下文时抛 IllegalStateException） ====================

    /**
     * 获取当前 RequestAttributes，无上下文时抛 IllegalStateException
     */
    public static RequestAttributes currentRequestAttributes() {
        return RequestContextHolder.currentRequestAttributes();
    }

    /**
     * 获取当前 ServletRequestAttributes，无上下文时抛 IllegalStateException
     */
    public static ServletRequestAttributes currentServletRequestAttributes() {
        return (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
    }

    /**
     * 获取当前 HttpServletRequest，无上下文时抛 IllegalStateException。
     * 适用于确保在 Web 请求线程中执行的场景（如 Controller、拦截器、切面）。
     */
    public static HttpServletRequest currentHttpServletRequest() {
        return currentServletRequestAttributes().getRequest();
    }

    /**
     * 获取当前 HttpServletResponse，无上下文时抛 IllegalStateException。
     * 注意：即使在请求上下文中，response 也可能为 null。
     */
    @Nullable
    public static HttpServletResponse currentHttpServletResponse() {
        return currentServletRequestAttributes().getResponse();
    }

    // ==================== 跨线程上下文传播 ====================

    /**
     * 开启请求上下文的线程继承（使用 InheritableThreadLocal），
     * 使子线程自动继承当前线程的 RequestAttributes。
     * <p>
     * 适用于直接 {@code new Thread()} / {@code CompletableFuture} 等无法使用 TaskDecorator 的场景。
     * 线程池场景请使用 {@link #wrapRunnable} 或 {@link #wrapCallable}。
     */
    public static void enableInheritable() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            RequestContextHolder.setRequestAttributes(attrs, true);
        }
    }

    /**
     * 捕获当前线程的完整上下文快照（RequestAttributes + RequestLocalContextHolder）
     *
     * @return 上下文快照，不会为 null
     */
    public static ContextSnapshot capture() {
        return new ContextSnapshot(
                RequestContextHolder.getRequestAttributes(),
                RequestLocalContextHolder.getLocalMap()
        );
    }

    /**
     * 在当前线程恢复指定上下文快照
     *
     * @param snapshot 上下文快照
     */
    public static void restore(ContextSnapshot snapshot) {
        if (snapshot.requestAttributes != null) {
            RequestContextHolder.setRequestAttributes(snapshot.requestAttributes);
        }
        if (snapshot.localMap != null) {
            RequestLocalContextHolder.setLocalMap(snapshot.localMap);
        }
    }

    /**
     * 清理当前线程的请求上下文
     */
    public static void reset() {
        RequestContextHolder.resetRequestAttributes();
        RequestLocalContextHolder.remove();
    }

    /**
     * 包装 Runnable，在子线程中自动传播上下文（RequestAttributes + RequestLocalContextHolder）
     *
     * @param runnable 原始任务
     * @return 包装后的任务
     */
    public static Runnable wrapRunnable(Runnable runnable) {
        ContextSnapshot snapshot = capture();
        return () -> {
            try {
                restore(snapshot);
                runnable.run();
            } finally {
                reset();
            }
        };
    }

    /**
     * 包装 Callable，在子线程中自动传播上下文（RequestAttributes + RequestLocalContextHolder）
     *
     * @param callable 原始任务
     * @param <T>      返回类型
     * @return 包装后的任务
     */
    public static <T> Callable<T> wrapCallable(Callable<T> callable) {
        ContextSnapshot snapshot = capture();
        return () -> {
            try {
                restore(snapshot);
                return callable.call();
            } finally {
                reset();
            }
        };
    }

    /**
     * 上下文快照，保存跨线程传播所需的全部状态
     */
    public static class ContextSnapshot {

        @Nullable
        private final RequestAttributes requestAttributes;

        @Nullable
        private final Map<String, Object> localMap;

        private ContextSnapshot(@Nullable RequestAttributes requestAttributes,
                                @Nullable Map<String, Object> localMap) {
            this.requestAttributes = requestAttributes;
            this.localMap = localMap;
        }

        @Nullable
        public RequestAttributes getRequestAttributes() {
            return requestAttributes;
        }

        @Nullable
        public Map<String, Object> getLocalMap() {
            return localMap;
        }
    }
}
