package com.github.sparkzxl.distributed.dubbo.environment;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * description: Nacos 配置属性
 *
 * @author zhouxinlei
 * @version 1.0
 * @since 2026-04-14 16:41:59
 */
@Data
@Slf4j
@Component
@ConfigurationProperties(prefix = "nacos")
public class NacosEnvironmentProperties {

    private String url;
    private String namespace;
    private String group = "DEFAULT_GROUP";
    private String username;
    private String password;
}
