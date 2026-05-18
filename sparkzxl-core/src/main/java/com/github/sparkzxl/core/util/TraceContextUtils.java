package com.github.sparkzxl.core.util;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;

/**
 * 统一的 TraceId 获取工具，基于 OpenTelemetry Span。
 * <p>
 * 优先级：OTel Span → 指定兜底值
 * <p>
 * 需配合 OTel Java Agent 使用，Agent 自动为 Servlet/WebFlux/Feign/Dubbo/Kafka 等创建 Span。
 *
 * @author zhouxinlei
 */
public final class TraceContextUtils {

    private static final String NA = "N/A";

    private TraceContextUtils() {
    }

    /**
     * 获取当前 traceId，兜底返回 "N/A"。
     */
    public static String getTraceId() {
        return getTraceId(NA);
    }

    /**
     * 获取当前 traceId，兜底返回指定的 fallback 值。
     *
     * @param fallback 无有效 Span 时的返回值
     */
    public static String getTraceId(String fallback) {
        SpanContext ctx = Span.current().getSpanContext();
        if (ctx.isValid()) {
            return ctx.getTraceId();
        }
        return fallback;
    }
}
