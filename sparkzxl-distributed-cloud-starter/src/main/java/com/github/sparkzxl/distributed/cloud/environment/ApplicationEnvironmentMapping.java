package com.github.sparkzxl.distributed.cloud.environment;

import lombok.Getter;

/**
 * Nacos 环境变量映射枚举
 * <p>
 * 定义系统环境变量与 Spring 属性之间的映射关系
 *
 * @author zhouxinlei
 * @version 1.0
 * @since 2026-04-15
 */
@Getter
public enum ApplicationEnvironmentMapping {

    /**
     * Nacos 配置中心服务器地址
     */
    NACOS_URL("NACOS_URL", "nacos.url"),

    /**
     * Nacos 配置中心服务器地址
     */
    NACOS_SERVER_ADDR("SPRING_CLOUD_NACOS_CONFIG_SERVER_ADDR", "nacos.url"),

    /**
     * Nacos 命名空间
     */
    NACOS_NAMESPACE("SPRING_CLOUD_NACOS_CONFIG_NAMESPACE", "nacos.namespace"),

    /**
     * Nacos 分组（使用 NACOS_GROUP 环境变量）
     */
    NACOS_GROUP("NACOS_GROUP", "nacos.group"),

    /**
     * Nacos 用户名（使用 NACOS_USERNAME 环境变量）
     */
    NACOS_USERNAME("NACOS_USERNAME", "nacos.username"),

    /**
     * Nacos 密码（使用 NACOS_PASSWORD 环境变量）
     */
    NACOS_PASSWORD("NACOS_PASSWORD", "nacos.password"),

    /**
     * 负载均衡器区域
     */
    LOADBALANCER_ZONE("LOADBALANCE_ZONE", "loadbalancer.zone");

    /**
     * 系统环境变量名
     */
    private final String envName;

    /**
     * Spring 属性名
     */
    private final String propertyName;

    ApplicationEnvironmentMapping(String envName, String propertyName) {
        this.envName = envName;
        this.propertyName = propertyName;
    }

    /**
     * 从系统环境变量读取值
     *
     * @return 环境变量值，如果不存在或为空则返回 null
     */
    public String getEnvValue() {
        String value = System.getenv(envName);
        return (value != null && !value.isEmpty()) ? value : null;
    }
}
