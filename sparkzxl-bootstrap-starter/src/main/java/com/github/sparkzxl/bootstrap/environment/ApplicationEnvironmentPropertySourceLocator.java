package com.github.sparkzxl.bootstrap.environment;

import com.github.sparkzxl.bootstrap.constant.enums.ApplicationEnvironmentEnum;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Locale;
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

    private static final String LOG_CONTEXT_TITLE = "Application bootstrap environment context:";

    private static final Log log = LogFactory.getLog(ApplicationEnvironmentPropertySourceLocator.class);

    @Override
    public PropertySource<?> locate(Environment environment) {
        Map<String, ResolvedEnvironmentValue> defaultCandidates = new LinkedHashMap<>();
        Map<String, ResolvedEnvironmentValue> resolvedValues = new LinkedHashMap<>();
        StringBuilder logContext = new StringBuilder(LOG_CONTEXT_TITLE);
        for (ApplicationEnvironmentEnum mapping : ApplicationEnvironmentEnum.values()) {
            String propertyName = mapping.getPropertyName();
            if (mapping.hasEnvValue()) {
                String envValue = mapping.getEnvValue();
                if (StringUtils.hasText(envValue)) {
                    if (resolvedValues.containsKey(propertyName)) {
                        appendLogContext(logContext, propertyName, envValue, isSensitive(mapping), "ignored");
                        continue;
                    }
                    boolean sensitive = isSensitive(mapping);
                    ResolvedEnvironmentValue resolvedValue = new ResolvedEnvironmentValue(envValue, sensitive);
                    resolvedValues.put(propertyName, resolvedValue);
                    appendLogContext(logContext, propertyName, envValue, sensitive, "loaded");
                }
            }
            if (!resolvedValues.containsKey(propertyName)) {
                String defaultValue = mapping.getDefaultValue();
                if (StringUtils.hasText(defaultValue)) {
                    defaultCandidates.put(propertyName, new ResolvedEnvironmentValue(defaultValue, isSensitive(mapping)));
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
                appendLogContext(logContext, propertyName, defaultValue.getValue(), defaultValue.isSensitive(), "default_ignored");
                continue;
            }
            properties.put(propertyName, defaultValue.getValue());
            appendLogContext(logContext, propertyName, defaultValue.getValue(), defaultValue.isSensitive(), "default_loaded");
        }

        if (hasLogContext(logContext)) {
            log.info(logContext.toString());
        }
        if (properties.isEmpty()) {
            return null;
        }
        return new MapPropertySource(PROPERTY_SOURCE_NAME, properties);
    }

    private static boolean hasLogContext(StringBuilder logContext) {
        return logContext.length() > LOG_CONTEXT_TITLE.length();
    }

    private static boolean isSensitive(ApplicationEnvironmentEnum mapping) {
        return mapping.isSensitive() || containsSensitiveKeyword(mapping.getEnvName()) || containsSensitiveKeyword(mapping.getPropertyName());
    }

    private static boolean containsSensitiveKeyword(String name) {
        String lowerName = name.toLowerCase(Locale.ROOT);
        return lowerName.contains("password") || lowerName.contains("secret")
                || lowerName.contains("token") || lowerName.contains("key");
    }

    private static void appendLogContext(StringBuilder debugContext, String propertyName,
                                         String value, boolean sensitive, String status) {
        debugContext.append(System.lineSeparator())
                .append(propertyName)
                .append("=")
                .append(formatLogValue(value, sensitive))
                .append(", status=")
                .append(status);
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

        private final String value;
        private final boolean sensitive;

        ResolvedEnvironmentValue(String value, boolean sensitive) {
            this.value = value;
            this.sensitive = sensitive;
        }

        String getValue() {
            return value;
        }

        boolean isSensitive() {
            return sensitive;
        }
    }
}
