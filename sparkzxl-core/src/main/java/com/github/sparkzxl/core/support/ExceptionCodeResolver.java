package com.github.sparkzxl.core.support;

import com.github.sparkzxl.core.support.code.ExceptionErrorCode;
import com.github.sparkzxl.core.support.code.IErrorCode;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * description: 异常格式化解析器
 * —— @ExceptionHandler 方法和 ExceptionEnhancedFilter 共用
 *
 * @author zhouxinlei
 * @version 1.0
 * @since 2026-04-30 14:18:57
 */
public class ExceptionCodeResolver {

    private static final ConcurrentHashMap<Class<? extends Throwable>, List<ResolverEntry>> RESOLVERS
            = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<Class<? extends Throwable>, MessageExtractor> MESSAGE_EXTRACTORS
            = new ConcurrentHashMap<>();

    public static void registerMessageExtractor(Class<? extends Throwable> type, MessageExtractor extractor) {
        MESSAGE_EXTRACTORS.put(type, extractor);
    }

    public static void register(Class<? extends Throwable> type, IErrorCode code) {
        RESOLVERS.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>())
                .add(new ResolverEntry(code, null));
    }

    public static void register(Class<? extends Throwable> type,
                                Predicate<String> predicate,
                                IErrorCode code) {
        RESOLVERS.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>())
                .add(new ResolverEntry(code, predicate));
    }

    public static void registerResolver(Class<? extends Throwable> type,
                                        Function<Throwable, ResolvedError> resolver) {
        RESOLVERS.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>())
                .add(new ResolverEntry(resolver));
    }

    public static ResolvedError resolve(Throwable exception) {
        return resolve(exception, ExceptionErrorCode.FAILURE);
    }

    public static ResolvedError resolve(Throwable exception, IErrorCode fallbackCode) {
        // 1. BaseException → 直接提取（B类，最高优先级）
        if (exception instanceof BaseException) {
            BaseException be = (BaseException) exception;
            return new ResolvedError(be.getErrorCode(), be.getErrorMsg());
        }
        // 2. 已注册的解析器（A类/C类/D类）
        List<ResolverEntry> entries = RESOLVERS.get(exception.getClass());
        if (entries != null) {
            String message = extractMessage(exception);
            for (ResolverEntry entry : entries) {
                // D类：自定义解析函数
                if (entry.resolver != null) {
                    ResolvedError result = entry.resolver.apply(exception);
                    if (result != null) {
                        return result;
                    }
                    continue;
                }
                // A/C类
                if (entry.predicate == null
                        || entry.predicate.test(message)) {
                    return new ResolvedError(entry.errorCode.getErrorCode(),
                            entry.errorCode.getErrorMsg());
                }
            }
        }
        // 3. 兜底
        return new ResolvedError(fallbackCode.getErrorCode(), exception.getMessage());
    }

    private ExceptionCodeResolver() {
    }

    private static String extractMessage(Throwable exception) {
        MessageExtractor extractor = MESSAGE_EXTRACTORS.get(exception.getClass());
        return extractor != null ? extractor.extract(exception) : exception.getMessage();
    }

    @FunctionalInterface
    public interface MessageExtractor {
        String extract(Throwable exception);
    }

    public static class ResolvedError {

        private final String errorCode;
        private final String errorMessage;

        public ResolvedError(String errorCode, String errorMessage) {
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }

        public String errorCode() {
            return errorCode;
        }

        public String errorMessage() {
            return errorMessage;
        }
    }

    private static class ResolverEntry {

        final IErrorCode errorCode;
        final Predicate<String> predicate;
        final Function<Throwable, ResolvedError> resolver;

        ResolverEntry(IErrorCode errorCode, Predicate<String> predicate) {
            this.errorCode = errorCode;
            this.predicate = predicate;
            this.resolver = null;
        }

        ResolverEntry(Function<Throwable, ResolvedError> resolver) {
            this.errorCode = null;
            this.predicate = null;
            this.resolver = resolver;
        }
    }
}
