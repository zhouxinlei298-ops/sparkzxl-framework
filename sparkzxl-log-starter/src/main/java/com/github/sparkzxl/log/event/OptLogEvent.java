package com.github.sparkzxl.log.event;

import com.github.sparkzxl.log.entity.OptRecordLog;
import org.springframework.context.ApplicationEvent;

/**
 * description: 操作日志事件
 *
 * @author zhouxinlei
 */
public class OptLogEvent extends ApplicationEvent {

    public OptLogEvent(OptRecordLog source) {
        super(source);
    }
}
