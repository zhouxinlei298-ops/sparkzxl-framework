package com.github.sparkzxl.trace.agent;

import com.github.sparkzxl.trace.properties.TraceProperties;

/**
 * 根据 TraceProperties 中的 OTel 配置，生成推荐的 JVM 启动参数。
 * <p>
 * OTel Java Agent 在 JVM 启动时加载，读取 -Dotel.* 系统属性，
 * 因此框架无法在运行时覆盖这些配置。此工具类生成推荐参数供使用者复制到启动脚本。
 */
public final class OtelAgentArguments {

    private static final String LINE_SEP = " \\\n    ";

    private OtelAgentArguments() {
    }

    /**
     * 根据配置生成推荐的 JVM 启动参数。
     *
     * @param config      OTel 配置属性
     * @param serviceName 服务名称（通常取 spring.application.name）
     * @return 格式化的 JVM 启动参数字符串
     */
    public static String generate(TraceProperties.OtelProperties config, String serviceName) {
        String resolvedServiceName = resolveServiceName(config, serviceName);

        return "-javaagent:opentelemetry-javaagent.jar" + LINE_SEP +
                "-Dotel.traces.exporter=" + config.getTracesExporter() + LINE_SEP +
                "-Dotel.exporter.otlp.endpoint=" + config.getEndpoint() + LINE_SEP +
                "-Dotel.logs.exporter=" + config.getLogsExporter() + LINE_SEP +
                "-Dotel.metrics.exporter=" + config.getMetricsExporter() + LINE_SEP +
                "-Dotel.instrumentation.jedis.enabled=" + config.isJedisEnabled() + LINE_SEP +
                "-Dotel.instrumentation.logback.enabled=" + config.isLogbackEnabled() + LINE_SEP +
                "-Dotel.resource.attributes=service.name=" + resolvedServiceName;
    }

    private static String resolveServiceName(TraceProperties.OtelProperties config, String springServiceName) {
        if (config.getServiceName() != null && !config.getServiceName().isEmpty()) {
            return config.getServiceName();
        }
        if (springServiceName != null && !springServiceName.isEmpty()) {
            return springServiceName;
        }
        return "unknown-service";
    }
}
