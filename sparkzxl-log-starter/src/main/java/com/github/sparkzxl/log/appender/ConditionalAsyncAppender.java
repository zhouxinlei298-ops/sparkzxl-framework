package com.github.sparkzxl.log.appender;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 支持通过 enabled 参数控制是否处理日志事件的异步 Appender
 * <p>
 * enabled=false 时 doAppend 直接返回，不创建队列线程、不转发事件。
 *
 * @author zhouxinlei
 * @since 2026-04-24
 */
@Setter
@Getter
public class ConditionalAsyncAppender extends AsyncAppender {

    private boolean enabled = true;

    @Override
    public void doAppend(ILoggingEvent eventObject) {
        if (!enabled) {
            return;
        }
        super.doAppend(eventObject);
    }
}
