package com.github.sparkzxl.distributed.cloud.environment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * Nacos 环境变量后置处理器
 * <p>
 * 在 Spring Boot 启动之前，将系统环境变量注入到 Spring Environment 中
 * 支持以下环境变量：
 * <ul>
 *   <li>SPRING_CLOUD_NACOS_CONFIG_SERVER_ADDR → nacos.url</li>
 *   <li>SPRING_CLOUD_NACOS_CONFIG_NAMESPACE → nacos.namespace</li>
 * </ul>
 *
 * @author zhouxinlei
 * @version 1.0
 * @since 2026-04-15
 */
@Slf4j
public class ApplicationEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String PROPERTY_SOURCE_NAME = "nacosEnvironmentVariables";
    private static final String ENV_SERVER_ADDR = "SPRING_CLOUD_NACOS_CONFIG_SERVER_ADDR";
    private static final String ENV_NAMESPACE = "SPRING_CLOUD_NACOS_CONFIG_NAMESPACE";
    private static final String PROPERTY_URL = "nacos.url";
    private static final String PROPERTY_NAMESPACE = "nacos.namespace";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> properties = new HashMap<>();

        // 从系统环境变量读取配置
        String envServerAddr = System.getenv(ENV_SERVER_ADDR);
        String envNamespace = System.getenv(ENV_NAMESPACE);

        if (envServerAddr != null && !envServerAddr.isEmpty()) {
            properties.put(PROPERTY_URL, envServerAddr);
            log.info("Injecting environment variable: {} = {}", ENV_SERVER_ADDR, envServerAddr);
        }

        if (envNamespace != null && !envNamespace.isEmpty()) {
            properties.put(PROPERTY_NAMESPACE, envNamespace);
            log.info("Injecting environment variable: {} = {}", ENV_NAMESPACE, envNamespace);
        }

        // 将属性添加到 Environment 中，优先级高于配置文件
        if (!properties.isEmpty()) {
            MapPropertySource propertySource = new MapPropertySource(PROPERTY_SOURCE_NAME, properties);
            environment.getPropertySources().addFirst(propertySource);
        }
    }
}
