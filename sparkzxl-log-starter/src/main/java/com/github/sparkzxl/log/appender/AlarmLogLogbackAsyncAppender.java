package com.github.sparkzxl.log.appender;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import com.github.sparkzxl.alarm.entity.AlarmRequest;
import com.github.sparkzxl.core.constant.BaseContextConstants;
import com.github.sparkzxl.core.spring.SpringContextUtils;
import com.github.sparkzxl.log.AlarmLogContext;
import com.github.sparkzxl.log.entity.AlarmLogInfo;
import com.github.sparkzxl.log.queue.AlarmTaskInfo;
import com.github.sparkzxl.log.queue.AlarmTaskQueue;
import com.github.sparkzxl.log.utils.ThrowableUtils;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;

import java.util.Objects;

/**
 * description: 日志告警Appender
 *
 * @author zhoux
 */
@Getter
@Setter
public class AlarmLogLogbackAsyncAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    private boolean enabled;
    private String robotId;
    private String title = "服务系统异常告警";

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (!enabled) {
            return;
        }
        if (!(eventObject instanceof LoggingEvent)) {
            return;
        }
        LoggingEvent loggingEvent = (LoggingEvent) eventObject;
        Level level = loggingEvent.getLevel();
        if (!level.isGreaterOrEqual(Level.WARN)) {
            return;
        }

        ThrowableProxy throwableProxy = (ThrowableProxy) loggingEvent.getThrowableProxy();
        if (Objects.nonNull(throwableProxy)) {
            handleWithThrowable(loggingEvent, throwableProxy);
        } else if (level.isGreaterOrEqual(Level.ERROR)) {
            handleWithoutThrowable(loggingEvent);
        }
    }

    private void handleWithThrowable(LoggingEvent loggingEvent, ThrowableProxy throwableProxy) {
        Throwable throwable = throwableProxy.getThrowable();
        if (!AlarmLogContext.match(throwable)) {
            return;
        }
        AlarmLogInfo.AlarmLogInfoBuilder builder = buildBaseAlarmLogInfo(loggingEvent);
        builder.throwableName(throwable.getClass().getName());
        StackTraceElement[] stackTraceElements = throwable.getStackTrace();
        if (ArrayUtils.isNotEmpty(stackTraceElements)) {
            fillLocationInfo(builder, stackTraceElements[0]);
        }
        String message = ThrowableUtils.dingTalkMarkdownContent(builder.build(), throwable);
        sendAlarmLog(message);
    }

    private void handleWithoutThrowable(LoggingEvent loggingEvent) {
        String exceptionClass = MDC.get("exceptionClass");
        if (StringUtils.isBlank(exceptionClass) || !AlarmLogContext.match(exceptionClass)) {
            return;
        }
        StackTraceElement[] callerData = loggingEvent.getCallerData();
        if (callerData == null || callerData.length == 0) {
            return;
        }
        AlarmLogInfo.AlarmLogInfoBuilder builder = buildBaseAlarmLogInfo(loggingEvent);
        builder.throwableName(exceptionClass);
        fillLocationInfo(builder, callerData[0]);
        String message = ThrowableUtils.dingTalkMarkdownContent(builder.build(), null);
        sendAlarmLog(message);
    }

    private AlarmLogInfo.AlarmLogInfoBuilder buildBaseAlarmLogInfo(LoggingEvent loggingEvent) {
        return AlarmLogInfo.builder()
                .applicationName(SpringContextUtils.getApplicationName())
                .environment(SpringContextUtils.getEnvironment())
                .message(loggingEvent.getFormattedMessage())
                .threadName(loggingEvent.getThreadName())
                .traceId(MDC.get(BaseContextConstants.LOG_TRACE_ID));
    }

    private void fillLocationInfo(AlarmLogInfo.AlarmLogInfoBuilder builder, StackTraceElement element) {
        builder.className(element.getClassName())
                .fileName(element.getFileName())
                .methodName(element.getMethodName())
                .lineNumber(element.getLineNumber());
    }

    private void sendAlarmLog(String message) {
        AlarmRequest alarmRequest = new AlarmRequest();
        alarmRequest.setTitle(title);
        alarmRequest.setContent(message);
        boolean produced = AlarmTaskQueue.getQueue().produce(new AlarmTaskInfo(robotId, alarmRequest));
        if (!produced) {
            addWarn("告警队列已满，丢弃告警消息");
        }
    }
}
