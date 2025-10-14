package com.github.sparkzxl.boot.config;


import com.alibaba.ttl.threadpool.TtlExecutors;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Stream;

import com.github.sparkzxl.core.context.ContextTaskDecorator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.task.TaskExecutionProperties;
import org.springframework.boot.task.TaskExecutorBuilder;
import org.springframework.boot.task.TaskExecutorCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * description: Spring 线程池配置
 *
 * @author zhouxinlei
 * @since 2022-12-14 10:31:10
 */
@EnableAsync
@Configuration
@Slf4j
public class ThreadPoolAutoConfig {

    @Bean
    public TaskDecorator taskDecorator() {
        return new ContextTaskDecorator();
    }


    @Bean
    public TaskExecutorBuilder taskExecutorBuilder(TaskExecutionProperties properties, ObjectProvider<TaskExecutorCustomizer> taskExecutorCustomizers, ObjectProvider<TaskDecorator> taskDecorator) {
        TaskExecutionProperties.Pool pool = properties.getPool();
        TaskExecutorBuilder builder = new TaskExecutorBuilder();
        builder = builder.queueCapacity(pool.getQueueCapacity());
        builder = builder.corePoolSize(pool.getCoreSize());
        builder = builder.maxPoolSize(pool.getMaxSize());
        builder = builder.allowCoreThreadTimeOut(pool.isAllowCoreThreadTimeout());
        builder = builder.keepAlive(pool.getKeepAlive());
        TaskExecutionProperties.Shutdown shutdown = properties.getShutdown();
        builder = builder.awaitTermination(shutdown.isAwaitTermination());
        builder = builder.awaitTerminationPeriod(shutdown.getAwaitTerminationPeriod());
        builder = builder.threadNamePrefix("async-task-");
        Stream stream = taskExecutorCustomizers.orderedStream();
        builder = builder.customizers(stream::iterator);
        builder = builder.taskDecorator(taskDecorator.getIfUnique());
        return builder;
    }
}
