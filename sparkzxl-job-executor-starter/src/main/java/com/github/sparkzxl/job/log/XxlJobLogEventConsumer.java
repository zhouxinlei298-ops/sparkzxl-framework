package com.github.sparkzxl.job.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.github.sparkzxl.core.constant.BaseContextConstants;
import com.xxl.job.core.context.XxlJobContext;
import com.xxl.job.core.context.XxlJobHelper;

import java.util.function.Consumer;

/**
 * description: XXL-JOB日志事件消费
 *
 * @author zhouxinlei
 * @since 2025-09-26 10:51:20
 */
public class XxlJobLogEventConsumer implements Consumer<ILoggingEvent> {

    @Override
    public void accept(ILoggingEvent event) {
        if (XxlJobContext.getXxlJobContext() != null) {
            // 1. 格式化日志信息
            String traceId = event.getMDCPropertyMap().getOrDefault(BaseContextConstants.LOG_TRACE_ID, "N/A");
            String tenantId = event.getMDCPropertyMap().getOrDefault(BaseContextConstants.TENANT_ID, "N/A");
            String logInfo = String.format(
                    "[%s] [traceId:%s] [tenantId:%s] %s",
                    event.getLevel(),
                    traceId,
                    tenantId,
                    event.getFormattedMessage()
            );
            XxlJobHelper.log(logInfo);
        }
    }
}
