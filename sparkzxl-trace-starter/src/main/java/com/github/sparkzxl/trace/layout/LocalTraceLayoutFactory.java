package com.github.sparkzxl.trace.layout;

import com.github.sparkzxl.core.trace.TraceLayoutFactory;

public class LocalTraceLayoutFactory implements TraceLayoutFactory {

    @Override
    public String type() {
        return "local";
    }

    @Override
    public String layoutClassName() {
        return "com.github.sparkzxl.trace.layout.LocalTraceIdMDCPatternLogbackLayout";
    }
}
