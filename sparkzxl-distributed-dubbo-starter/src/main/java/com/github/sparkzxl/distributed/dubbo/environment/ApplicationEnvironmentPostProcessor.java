package com.github.sparkzxl.distributed.dubbo.environment;

import com.github.sparkzxl.core.constant.enums.ApplicationEnvironmentEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
 *
 * @author zhouxinlei
 * @version 1.0
 * @since 2026-04-15
 */
@Slf4j
public class ApplicationEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String PROPERTY_SOURCE_NAME = "applicationEnvironmentVariables";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> properties = new HashMap<>();

        // 遍历所有映射配置，从环境变量读取并注入
        for (ApplicationEnvironmentEnum mapping : ApplicationEnvironmentEnum.values()) {
            String envValue = mapping.getEnvValue();
            // 如果环境变量未设置，则从 Spring Environment 中获取已存在的属性值
            if (StringUtils.isEmpty(envValue)) {
                envValue = environment.getProperty(mapping.getPropertyName());
                if (StringUtils.isNotEmpty(envValue)) {
                    envValue = ApplicationEnvironmentEnum.appendDefaultPort(envValue, 8848);
                }
            }
            if (StringUtils.isNotEmpty(envValue)) {
                properties.putIfAbsent(mapping.getPropertyName(), envValue);
                log.info("loaded environment variable: {} = {}", mapping.getEnvName(), envValue);
            }
        }

        // 将属性添加到 Environment 中，优先级高于配置文件
        if (!properties.isEmpty()) {
            MapPropertySource propertySource = new MapPropertySource(PROPERTY_SOURCE_NAME, properties);
            environment.getPropertySources().addFirst(propertySource);
        }
    }
}
