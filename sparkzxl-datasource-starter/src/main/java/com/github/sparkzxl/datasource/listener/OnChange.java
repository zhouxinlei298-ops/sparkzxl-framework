package com.github.sparkzxl.datasource.listener;

/**
 * description: 配置发生变更
 *
 * @author zhouxinlei
 * @since 2025-08-21 15:06:02
 */
public interface OnChange {

    void change(boolean initialized, String configInfo);
}
