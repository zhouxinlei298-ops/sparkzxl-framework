package com.github.sparkzxl.log.appender;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import lombok.Getter;
import lombok.Setter;

import java.util.function.Consumer;

/**
 * description: 定时任务日志Appender
 *
 * @author zhouxinlei
 * @since 2025-09-26 10:29:37
 */
@Getter
@Setter
@SuppressWarnings("all")
public class ScheduleJobLogAsyncAppender extends AsyncAppender {

    private String consumerClass;

    private Consumer<ILoggingEvent> consumer;

    @Override
    public void start() {
        if (consumerClass == null || consumerClass.trim().isEmpty()) {
            return;
        }
        try {
            Class<?> clazz = Class.forName(consumerClass);
            if (!Consumer.class.isAssignableFrom(clazz)) {
                addError(consumerClass + "未实现java.util.function.Consumer接口");
                return;
            }
            consumer = (Consumer<ILoggingEvent>) clazz.getDeclaredConstructor().newInstance();
            addInfo("成功实例化Consumer: " + consumerClass);
            super.start();
        } catch (ClassNotFoundException e) {
            addInfo("Consumer实现类未找到，跳过Appender启动: " + consumerClass);
        } catch (NoSuchMethodException e) {
            addError("Consumer实现类缺少public无参构造函数: " + consumerClass, e);
        } catch (Exception e) {
            addError("实例化Consumer失败: " + consumerClass, e);
        }
    }

    @Override
    public void doAppend(ILoggingEvent event) {
        if (!isStarted() || consumer == null) {
            return;
        }
        try {
            consumer.accept(event);
        } catch (Exception e) {
            addError("Consumer处理日志事件失败", e);
        }
    }
}
