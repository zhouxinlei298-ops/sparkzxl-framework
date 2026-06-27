package com.github.sparkzxl.bootstrap.environment;

import com.github.sparkzxl.bootstrap.constant.enums.ApplicationEnvironmentEnum;
import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
        Map<String, ResolvedEnvironmentValue> defaultCandidates = new LinkedHashMap<>();
        Map<String, Boolean> resolvedFromEnv = new LinkedHashMap<>();
        Map<String, ResolvedEnvironmentValue> resolvedValues = new LinkedHashMap<>();
        for (ApplicationEnvironmentEnum mapping : ApplicationEnvironmentEnum.values()) {
            String propertyName = mapping.getPropertyName();
            if (!resolvedFromEnv.getOrDefault(propertyName, false) && mapping.hasEnvValue()) {
                String envValue = mapping.getEnvValue();
                if (StringUtils.hasText(envValue)) {
                    resolvedFromEnv.put(propertyName, true);
                    resolvedValues.put(propertyName, new ResolvedEnvironmentValue(mapping.getEnvName(), envValue, mapping.isSensitive()));
                    System.out.println("loaded environment variable: " + mapping.getEnvName() + " = " + formatLogValue(envValue, mapping.isSensitive()) + " -> " + propertyName);
                }
            }
            if (!resolvedFromEnv.getOrDefault(propertyName, false)) {
                String defaultValue = mapping.getDefaultValue();
                if (StringUtils.hasText(defaultValue)) {
                    defaultCandidates.put(propertyName, new ResolvedEnvironmentValue(mapping.getEnvName(), defaultValue, mapping.isSensitive()));
                }
            }
        }

        Map<String, Object> properties = new LinkedHashMap<>();
        for (Map.Entry<String, ResolvedEnvironmentValue> entry : resolvedValues.entrySet()) {
            properties.put(entry.getKey(), entry.getValue().getValue());
        }
        for (Map.Entry<String, ResolvedEnvironmentValue> entry : defaultCandidates.entrySet()) {
            String propertyName = entry.getKey();
            ResolvedEnvironmentValue defaultValue = entry.getValue();
            if (environment.getProperty(propertyName) != null) {
                continue;
            }
            properties.put(propertyName, defaultValue.getValue());
            System.out.println("loaded default value: " + defaultValue.getSourceName() + " = " + formatLogValue(defaultValue.getValue(), defaultValue.isSensitive()) + " -> " + propertyName);
        }

        if (properties.isEmpty()) {
            return null;
        }
        return new MapPropertySource(PROPERTY_SOURCE_NAME, properties);
    }

    private static String formatLogValue(String value, boolean sensitive) {
        if (!sensitive) {
            return value;
        }
        return "******(length=" + value.length() + ", sha256=" + digest(value) + ")";
    }

    private static String digest(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int index = 0; index < digest.length && index < 6; index++) {
                String item = Integer.toHexString(digest[index] & 0xff);
                if (item.length() == 1) {
                    hex.append('0');
                }
                hex.append(item);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            return "unavailable";
        }
    }

    private static class ResolvedEnvironmentValue {

        private final String sourceName;
        private final String value;
        private final boolean sensitive;

        ResolvedEnvironmentValue(String sourceName, String value, boolean sensitive) {
            this.sourceName = sourceName;
            this.value = value;
            this.sensitive = sensitive;
        }

        String getSourceName() {
            return sourceName;
        }

        String getValue() {
            return value;
        }

        boolean isSensitive() {
            return sensitive;
        }
    }
}
