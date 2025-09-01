package com.github.sparkzxl.log.appender;

import ch.qos.logback.classic.spi.LoggingEvent;
import com.aliyun.openservices.log.logback.LoghubAppender;
import lombok.Getter;
import lombok.Setter;

/**
 * description: aliyun sls log 增加开关属性
 *
 * @author zhouxinlei
 * @since 2025-08-11 09:17:10
 */
@Getter
@Setter
public class AliYunLogAppender extends LoghubAppender<LoggingEvent> {

    private boolean enabled;

    @Override
    public void start() {
        if (enabled) {
            super.start();
        }else {
            started = false;
        }
    }

    @Override
    public void doAppend(LoggingEvent eventObject) {
        if (enabled) {
            super.doAppend(eventObject);
        }
    }
}
