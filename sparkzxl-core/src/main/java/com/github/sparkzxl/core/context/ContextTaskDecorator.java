package com.github.sparkzxl.core.context;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Map;

/**
 * description: 复制主线程上下文，创建独立副本
 *
 * @author zhouxinlei
 * @since 2025-10-11 09:57:15
 */
@Slf4j
public class ContextTaskDecorator implements TaskDecorator {

    @Override
    public @NotNull Runnable decorate(@NotNull Runnable runnable) {
        // 注意：这里的代码仍在「主线程」执行（任务提交时），而非子线程
        // 核心逻辑：包装原任务，在子线程执行原任务前后插入上下文操作
        Map<String, Object> localMap = RequestLocalContextHolder.getLocalMap();
        Map<String, String> mdcContextMap = MDC.getCopyOfContextMap();
        final RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        return () -> {
            try {
                // 1. 子线程执行任务前：复制主线程上下文，创建独立副本
                RequestContextHolder.setRequestAttributes(requestAttributes);
                RequestLocalContextHolder.setLocalMap(localMap);
                if (mdcContextMap != null) {
                    MDC.setContextMap(mdcContextMap);
                }
                // 2. 执行原业务任务（子线程中）
                runnable.run();
            } finally {
                // 3. 子线程执行任务后：清理上下文，避免线程池复用污染
                RequestContextHolder.resetRequestAttributes();
                RequestLocalContextHolder.remove();
                MDC.clear();
            }
        };
    }
}
