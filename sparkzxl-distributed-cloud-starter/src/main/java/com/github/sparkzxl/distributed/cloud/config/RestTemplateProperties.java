package com.github.sparkzxl.distributed.cloud.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * description: RestTemplate 配置属性
 *
 * @author zhouxinlei
 * @since 2026-04-11
 */
@Data
@ConfigurationProperties(prefix = "resttemplate")
public class RestTemplateProperties {

    /**
     * 连接超时时间（毫秒）
     */
    private int connectTimeout = 5000;

    /**
     * 读取超时时间（毫秒）
     */
    private int readTimeout = 10000;

    /**
     * 连接池配置
     */
    private ConnectionPool connectionPool = new ConnectionPool();

    /**
     * 是否禁用 SSL 验证
     */
    private boolean disableSslValidation = false;

    @Data
    public static class ConnectionPool {
        /**
         * 连接池最大连接数
         */
        private int maxTotal = 200;

        /**
         * 单路由最大连接数
         */
        private int defaultMaxPerRoute = 50;
    }
}
