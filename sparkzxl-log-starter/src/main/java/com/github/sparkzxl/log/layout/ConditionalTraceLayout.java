package com.github.sparkzxl.log.layout;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Layout;
import com.github.sparkzxl.core.constant.BaseContextConstants;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.apm.toolkit.log.logback.v1.x.mdc.TraceIdMDCPatternLogbackLayout;
import org.slf4j.MDC;

/**
 * 根据 spring.trace.type 条件选择 Layout
 * <p>
 * skywalking → TraceIdMDCPatternLogbackLayout（SkyWalking 自动注入 tid）
 * local（默认）→ PatternLayout（直接读取 MDC 中由 Interceptor 设置的 tid）
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
            super.start();
        }
    }

    @Override
    public String doLayout(ILoggingEvent event) {
        String tenantId = MDC.get(BaseContextConstants.TENANT_ID);
        System.out.println("tenantId："+tenantId);
        String tid = MDC.get(BaseContextConstants.LOG_TRACE_ID);
        System.out.println("tid："+tid);
        if (delegate != null) {
            return delegate.doLayout(event);
        }
        return super.doLayout(event);
    }
}
