package com.github.sparkzxl.trace.provider;

import com.github.sparkzxl.core.trace.TraceIdContext;
import com.github.sparkzxl.core.trace.TraceTypeEnum;
import org.apache.skywalking.apm.toolkit.trace.TraceContext;

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
