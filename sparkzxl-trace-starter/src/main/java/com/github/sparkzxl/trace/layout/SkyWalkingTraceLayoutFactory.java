package com.github.sparkzxl.trace.layout;

import com.github.sparkzxl.core.trace.TraceLayoutFactory;

public class SkyWalkingTraceLayoutFactory implements TraceLayoutFactory {

    @Override
    public String type() {
        return "skywalking";
    }

    @Override
    public String layoutClassName() {
        return "org.apache.skywalking.apm.toolkit.log.logback.v1.x.mdc.TraceIdMDCPatternLogbackLayout";
    }
}
