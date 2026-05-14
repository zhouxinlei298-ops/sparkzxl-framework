package com.github.sparkzxl.log.layout;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Layout;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.apm.toolkit.log.logback.v1.x.mdc.TraceIdMDCPatternLogbackLayout;

/**
 * 根据 spring.trace.type 条件选择 Layout
 * <p>
 * skywalking -> TraceIdMDCPatternLogbackLayout（SkyWalking 自动注入 tid）
 * local（默认）-> LocalTraceIdMDCPatternLogbackLayout（tid 不存在时输出 N/A）
 *
 * @author zhouxinlei
 * @since 2026-04-24
 */
@Setter
@Getter
public class ConditionalTraceLayout extends PatternLayout {

    private String traceType = "local";
    private Layout<ILoggingEvent> delegate;

    @Override
    public void start() {
        if ("skywalking".equalsIgnoreCase(traceType)) {
            TraceIdMDCPatternLogbackLayout swLayout = new TraceIdMDCPatternLogbackLayout();
            swLayout.setPattern(getPattern());
            swLayout.setContext(getContext());
            swLayout.start();
            this.delegate = swLayout;
        } else {
            LocalTraceIdMDCPatternLogbackLayout localLayout = new LocalTraceIdMDCPatternLogbackLayout();
            localLayout.setPattern(getPattern());
            localLayout.setContext(getContext());
            localLayout.start();
            this.delegate = localLayout;
        }
    }

    @Override
    public String doLayout(ILoggingEvent event) {
        if (delegate != null) {
            return delegate.doLayout(event);
        }
        return super.doLayout(event);
    }
}
