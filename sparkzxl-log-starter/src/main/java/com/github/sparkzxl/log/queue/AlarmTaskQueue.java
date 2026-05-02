package com.github.sparkzxl.log.queue;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * description: 告警任务消息队列
 *
 * @author zhouxinlei
 * @since 2022-12-26 11:17:14
 */
public class AlarmTaskQueue {

    private static final int DEFAULT_MAX_CAPACITY = 1000;
    private static final Queue<AlarmTaskInfo> QUEUE = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger SIZE = new AtomicInteger(0);

    private final int maxCapacity;

    private AlarmTaskQueue() {
        this(DEFAULT_MAX_CAPACITY);
    }

    private AlarmTaskQueue(int maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    /**
     * 单例队列
     *
     * @return AlarmTaskQueue
     */
    public static AlarmTaskQueue getQueue() {
        return SingletonHolder.SINGLETON;
    }

    public boolean produce(AlarmTaskInfo message) {
        if (SIZE.get() >= maxCapacity) {
            return false;
        }
        QUEUE.add(message);
        SIZE.incrementAndGet();
        return true;
    }

    /**
     * 消费队列
     *
     * @return AlarmTaskInfo
     */
    public AlarmTaskInfo consume() {
        AlarmTaskInfo task = QUEUE.poll();
        if (task != null) {
            SIZE.decrementAndGet();
        }
        return task;
    }

    public int size() {
        return SIZE.get();
    }

    private static class SingletonHolder {

        private static final AlarmTaskQueue SINGLETON = new AlarmTaskQueue();
    }

}
