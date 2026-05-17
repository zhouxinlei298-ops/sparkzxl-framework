package com.github.sparkzxl.trace.layout;

import ch.qos.logback.classic.pattern.MDCConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.util.OptionHelper;

public class LocalMDCPatternConverter extends MDCConverter {

    private static final String CONVERT_TRACE_ID_KEY = "tid";
    private boolean convert4Tid = false;

    @Override
    public void start() {
        super.start();
        String[] key = OptionHelper.extractDefaultReplacement(getFirstOption());
        if (key != null && key.length > 0 && CONVERT_TRACE_ID_KEY.equals(key[0])) {
            this.convert4Tid = true;
        }
    }

    @Override
    public String convert(ILoggingEvent event) {
        if (convert4Tid) {
            return convertTid(event);
        }
        return super.convert(event);
    }

    private String convertTid(ILoggingEvent event) {
        String tid = event.getMDCPropertyMap().get(CONVERT_TRACE_ID_KEY);
        return (tid != null && !tid.isEmpty()) ? tid : "TID: N/A";
    }
}
