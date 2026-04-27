package com.github.sparkzxl.log.consumer;

import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.function.Consumer;

/**
 * description: 日志事件消费
 *
 * @author zhouxinlei
 * @since 2025-09-26 10:51:20
 */
public class LogEventConsumer implements Consumer<ILoggingEvent> {

    @Override
    public void accept(ILoggingEvent iLoggingEvent) {

    }
}
