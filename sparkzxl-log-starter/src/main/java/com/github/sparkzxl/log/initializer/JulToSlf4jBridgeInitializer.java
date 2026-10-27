package com.github.sparkzxl.log.initializer;

import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

/**
 * <p>
 * JUL 到 SLF4J 的桥接初始化器
 * </p>
 *
 * <p>IAM 等框架的协议错误出口日志（4xx WARN 单行 / 5xx ERROR 堆栈）经 JUL 记录；未桥接时只走
 * JUL 默认 ConsoleHandler（stderr），绕开本 starter 的文件/Kafka/logstash 统一管道。本初始化器
 * 与 {@link MdcContextAdapterInitializer} 同通道注册（spring.factories），在容器刷新前完成桥接：
 * 先移除 JUL 默认 handler 再安装，避免 stderr 与管道双打。</p>
 *
 * <p>开关：{@code logging.jul-bridge}，缺省开启。</p>
 *
 * @author zhouxinlei
 * @version 1.0
 * @since 2026-10-27
 */
public class JulToSlf4jBridgeInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {

    private static volatile boolean installed;

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        Environment environment = context.getEnvironment();
        if (!"true".equalsIgnoreCase(environment.getProperty("logging.jul-bridge", "true"))) {
            return;
        }
        if (installed) {
            return;
        }
        synchronized (JulToSlf4jBridgeInitializer.class) {
            if (installed) {
                return;
            }
            SLF4JBridgeHandler.removeHandlersForRootLogger();
            SLF4JBridgeHandler.install();
            installed = true;
        }
    }

    @Override
    public int getOrder() {
        // 在 MDC 适配器初始化之后执行：桥接只依赖 SLF4J 已完成静态绑定。
        return HIGHEST_PRECEDENCE + 20;
    }
}
