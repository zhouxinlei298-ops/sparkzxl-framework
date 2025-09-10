package com.github.sparkzxl.core.context;

/**
 * description: 获取traceId
 *
 * @author zhouxinlei
 * @since 2025-08-30 11:08:31
 */
public interface TraceIdContext {

    String getTraceId();

    String type();
}
