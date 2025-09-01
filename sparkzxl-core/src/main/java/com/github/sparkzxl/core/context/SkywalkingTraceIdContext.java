package com.github.sparkzxl.core.context;

import com.github.sparkzxl.core.constant.enums.TraceTypeEnum;
import org.apache.skywalking.apm.toolkit.trace.TraceContext;

/**
 * description: skywalking traceId 获取
 *
 * @author zhouxinlei
 * @since 2025-08-30 11:09:43
 */
public class SkywalkingTraceIdContext implements TraceIdContext {

    @Override
    public String getTraceId() {
        return TraceContext.traceId();
    }

    @Override
    public String type() {
        return TraceTypeEnum.SKYWALKING.name();
    }
}
