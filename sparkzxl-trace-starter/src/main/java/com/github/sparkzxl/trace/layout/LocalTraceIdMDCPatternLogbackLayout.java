package com.github.sparkzxl.trace.layout;

import ch.qos.logback.classic.PatternLayout;

public class LocalTraceIdMDCPatternLogbackLayout extends PatternLayout {

    static {
        DEFAULT_CONVERTER_MAP.put("X", LocalMDCPatternConverter.class.getName());
    }
}
