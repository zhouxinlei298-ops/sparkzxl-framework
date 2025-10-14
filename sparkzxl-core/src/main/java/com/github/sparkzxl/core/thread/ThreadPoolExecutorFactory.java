package com.github.sparkzxl.core.thread;

import com.github.sparkzxl.core.context.ContextTaskDecorator;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;

/**
 * description: 线程池执行器工厂
 *
 * @author zhouxinlei
 * @since 2025-10-11 16:15:26
 */
public class ThreadPoolExecutorFactory {

    /**
     * 获取线程池
     *
     * @param corePoolSize             核心线程池大小
     * @param maxPoolSize              最大线程数
     * @param queueCapacity            队列容量
     * @param threadNamePrefix         线程名字前缀
     * @param rejectedExecutionHandler 拒绝策略
     * @return ExecutorService
     */
    public static ExecutorService getThreadPoolExecutor(int corePoolSize,
                                                        int maxPoolSize,
                                                        int queueCapacity,
                                                        String threadNamePrefix,
                                                        RejectedExecutionHandler rejectedExecutionHandler) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        // 设置线程池关闭的时候等待所有任务都完成再继续销毁其他的Bean
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 设置这个执行器在关闭时应该阻止的最大秒数
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(rejectedExecutionHandler);
        // 设置装饰器
        TaskDecorator taskDecorator = new ContextTaskDecorator();
        executor.setTaskDecorator(taskDecorator);
        executor.initialize();
        return executor.getThreadPoolExecutor();
    }

    /**
     * 获取线程池
     *
     * @param corePoolSize             核心线程池大小
     * @param maxPoolSize              最大线程数
     * @param keepAliveSeconds         存活时间
     * @param queueCapacity            队列容量
     * @param threadNamePrefix         线程名字前缀
     * @param rejectedExecutionHandler 拒绝策略
     * @return ExecutorService
     */
    public static ExecutorService getThreadPoolExecutor(int corePoolSize,
                                                        int maxPoolSize,
                                                        int keepAliveSeconds,
                                                        int queueCapacity,
                                                        String threadNamePrefix,
                                                        RejectedExecutionHandler rejectedExecutionHandler) {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        // 设置线程池关闭的时候等待所有任务都完成再继续销毁其他的Bean
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 设置这个执行器在关闭时应该阻止的最大秒数
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(rejectedExecutionHandler);
        // 设置装饰器
        TaskDecorator taskDecorator = new ContextTaskDecorator();
        executor.setTaskDecorator(taskDecorator);
        executor.initialize();
        return executor.getThreadPoolExecutor();
    }

    /**
     * 获取线程池
     *
     * @param corePoolSize             核心线程池大小
     * @param maxPoolSize              最大线程数
     * @param keepAliveSeconds         存活时间
     * @param queueCapacity            队列容量
     * @param allowCoreThreadTimeOut   允许核心线程超时
     * @param prestartAllCoreThreads   预先启动所有核心线程
     * @param threadNamePrefix         线程名字前缀
     * @param rejectedExecutionHandler 拒绝策略
     * @return ExecutorService
     */
    public static ExecutorService getThreadPoolExecutor(int corePoolSize,
                                                        int maxPoolSize,
                                                        int keepAliveSeconds,
                                                        int queueCapacity,
                                                        boolean allowCoreThreadTimeOut,
                                                        boolean prestartAllCoreThreads,
                                                        String threadNamePrefix,
                                                        RejectedExecutionHandler rejectedExecutionHandler) {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setQueueCapacity(queueCapacity);
        executor.setAllowCoreThreadTimeOut(allowCoreThreadTimeOut);
        executor.setPrestartAllCoreThreads(prestartAllCoreThreads);
        executor.setThreadNamePrefix(threadNamePrefix);
        // 设置线程池关闭的时候等待所有任务都完成再继续销毁其他的Bean
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 设置这个执行器在关闭时应该阻止的最大秒数
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(rejectedExecutionHandler);
        // 设置装饰器
        TaskDecorator taskDecorator = new ContextTaskDecorator();
        executor.setTaskDecorator(taskDecorator);
        executor.initialize();
        return executor.getThreadPoolExecutor();
    }
}
