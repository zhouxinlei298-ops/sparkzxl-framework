package com.github.sparkzxl.bootstrap.environment;

import com.github.sparkzxl.bootstrap.constant.enums.ApplicationEnvironmentEnum;
import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Nacos 环境变量属性源定位器
 * <p>
 * 在 Spring Cloud bootstrap 阶段执行，晚于 bootstrap-test.yml 等配置文件的加载，
 * 通过返回高优先级 PropertySource，确保真实系统环境变量覆盖配置文件中的同名属性。
 *
 * @author zhouxinlei
 * @version 1.0
 * @since 2026-04-15
 */
public class ApplicationEnvironmentPropertySourceLocator implements PropertySourceLocator {

    private static final String PROPERTY_SOURCE_NAME = "applicationEnvironmentVariables";

    @Override
    public PropertySource<?> locate(Environment environment) {
        Map<String, String> defaultCandidates = new LinkedHashMap<>();
        Map<String, Boolean> resolvedFromEnv = new LinkedHashMap<>();
        Map<String, String> resolvedValues = new LinkedHashMap<>();
        for (ApplicationEnvironmentEnum mapping : ApplicationEnvironmentEnum.values()) {
            String propertyName = mapping.getPropertyName();
            if (!resolvedFromEnv.getOrDefault(propertyName, false) && mapping.hasEnvValue()) {
                String envValue = mapping.getEnvValue();
                if (StringUtils.hasText(envValue)) {
                    resolvedFromEnv.put(propertyName, true);
                    resolvedValues.put(propertyName, envValue);
                    System.out.println("loaded environment variable: " + mapping.getEnvName() + " = " + envValue + " -> " + propertyName);
                }
            }
            if (!resolvedFromEnv.getOrDefault(propertyName, false)) {
                String defaultValue = mapping.getDefaultValue();
                if (StringUtils.hasText(defaultValue)) {
                    defaultCandidates.put(propertyName, defaultValue);
                }
            }
        }

        Map<String, Object> properties = new LinkedHashMap<>(resolvedValues);
        for (Map.Entry<String, String> entry : defaultCandidates.entrySet()) {
            String propertyName = entry.getKey();
            String defaultValue = entry.getValue();
            if (environment.getProperty(propertyName) != null) {
                continue;
            }
            properties.put(propertyName, defaultValue);
            System.out.println("loaded default value: " + propertyName + " = " + defaultValue);
        }

        if (properties.isEmpty()) {
            return null;
        }
        return new MapPropertySource(PROPERTY_SOURCE_NAME, properties);
    }
}
