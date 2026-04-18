package com.github.sparkzxl.core.context;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * description: 复制主线程上下文，创建独立副本
 *
 * @author zhouxinlei
 * @since 2025-10-11 09:57:15
 */
@Slf4j
public class ContextTaskDecorator implements TaskDecorator {

    // 反射需要的常量（提前定义避免重复创建）
    // 动态数据源切换类名
    private static final String DATA_SOURCE_HOLDER_CLASS = "com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder";
    private static final Class<?> DATA_SOURCE_HOLDER;
    private static final Method PUSH_METHOD;
    private static final Method CLEAR_METHOD;

    // 静态块：初始化反射信息（仅执行一次）
    static {
        Class<?> holderClass = null;
        Method pushMethod = null;
        Method clearMethod = null;
        try {
            // 尝试加载类
            holderClass = Class.forName(DATA_SOURCE_HOLDER_CLASS);
            // 尝试获取push方法（假设参数为String类型的tenantId）
            pushMethod = holderClass.getMethod("push", String.class);
            // 尝试获取clear方法（无参数）
            clearMethod = holderClass.getMethod("clear");
        } catch (ClassNotFoundException e) {
            log.debug("未引入DynamicDataSourceContextHolder依赖，跳过初始化");
        } catch (NoSuchMethodException e) {
            log.warn("DynamicDataSourceContextHolder存在，但方法定义不符合预期:{}", e.getMessage());
        } catch (Exception e) {
            log.error("初始化DynamicDataSourceContextHolder反射信息失败:{}", e.getMessage());
        }
        DATA_SOURCE_HOLDER = holderClass;
        PUSH_METHOD = pushMethod;
        CLEAR_METHOD = clearMethod;
    }

    @Override
    public @NotNull Runnable decorate(@NotNull Runnable runnable) {
        // 注意：这里的代码仍在「主线程」执行（任务提交时），而非子线程
        // 核心逻辑：包装原任务，在子线程执行原任务前后插入上下文操作
        RequestContextHelper.ContextSnapshot snapshot = RequestContextHelper.capture();
        Map<String, String> mdcContextMap = MDC.getCopyOfContextMap();
        String tenantId = RequestLocalContextHolder.getTenantId();
        return () -> {
            try {
                // 1. 子线程执行任务前：恢复主线程上下文
                RequestContextHelper.restore(snapshot);
                if (mdcContextMap != null) {
                    MDC.setContextMap(mdcContextMap);
                }
                // 2. 动态执行DynamicDataSourceContextHolder.push(tenantId)（若依赖存在）
                if (DATA_SOURCE_HOLDER != null && PUSH_METHOD != null && StringUtils.isNotEmpty(tenantId)) {
                    try {
                        PUSH_METHOD.invoke(null, tenantId);
                    } catch (Exception e) {
                        log.warn("调用DynamicDataSourceContextHolder.push失败", e);
                    }
                }
                // 3. 执行原业务任务（子线程）
                runnable.run();
            } finally {
                // 4. 动态执行DynamicDataSourceContextHolder.clear()（若依赖存在）
                if (DATA_SOURCE_HOLDER != null && CLEAR_METHOD != null) {
                    try {
                        CLEAR_METHOD.invoke(null);
                    } catch (Exception e) {
                        log.warn("调用DynamicDataSourceContextHolder.clear失败", e);
                    }
                }
                // 5. 清理上下文
                RequestContextHelper.reset();
                MDC.clear();
            }
        };
    }
}
