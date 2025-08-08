package com.github.sparkzxl.alarm.properties;

import com.github.sparkzxl.alarm.constant.AlarmConstant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * description: 告警线程池参数配置-用于异步处理
 *
 * @author zhouxinlei
 * @since 2022-05-24 10:48:46
 */
@Setter
@Getter
@ConfigurationProperties(prefix = AlarmConstant.ALARM_THREAD_CONFIG_PREFIX)
public class AlarmThreadPoolProperties {

    private static final int DEFAULT_CORE_SIZE = Runtime.getRuntime().availableProcessors() + 1;
    /**
     * 线程池维护线程的最小数量, 选填
     */
    private int coreSize = DEFAULT_CORE_SIZE;
    /**
     * 线程池维护线程的最大数量, 选填
     */
    private int maxSize = DEFAULT_CORE_SIZE * 2;
    /**
     * 空闲线程的存活时间, 选填
     */
    private int keepAliveSeconds = 60;
    /**
     * 持有等待执行的任务队列, 选填
     */
    private int queueCapacity = 10;
    /**
     * 线程名称前缀, 选填
     */
    private String threadNamePrefix = AlarmConstant.DEFAULT_THREAD_NAME_PREFIX;

}
