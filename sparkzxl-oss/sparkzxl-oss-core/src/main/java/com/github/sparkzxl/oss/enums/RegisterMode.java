package com.github.sparkzxl.oss.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * description: 注册类型枚举
 *
 * @author zhouxinlei
 */
@Getter
@RequiredArgsConstructor
public enum RegisterMode {
    /**
     * JDBC
     */
    JDBC,
    FILE,
    YAML,

    /**
     * 不注册任何静态配置：配置完全由宿主在运行时提供（例如发布快照）。
     *
     * <p>该模式下 {@code oss.configs} 允许缺省或为空；任何静态加载入口都明确拒绝，
     * 不读取环境变量、Nacos 或文件，也不回退任何默认客户端。</p>
     */
    NONE

}
