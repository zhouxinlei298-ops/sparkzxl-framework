package com.github.sparkzxl.core.context;

/**
 * description: 资源清理接口，用于在请求处理完成后清理资源
 *
 * @author zhouxinlei
 * @since 2025-06-22 11:37:34
 */
public interface ResourceCleaner {

    /**
     * 资源清理
     */
    void cleanup();
}
