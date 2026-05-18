package com.github.sparkzxl.trace.config;

import com.github.sparkzxl.trace.agent.OtelAgentArguments;
import com.github.sparkzxl.trace.properties.TraceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;


@Configuration
@EnableConfigurationProperties(TraceProperties.class)
@ConditionalOnProperty(name = "sparkzxl.trace.enabled", havingValue = "true", matchIfMissing = true)
@Import({
        ServletTraceConfiguration.class,
        GatewayTraceConfiguration.class,
        FeignTraceConfiguration.class
})
public class TraceAutoConfiguration implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(TraceAutoConfiguration.class);

    private final TraceProperties traceProperties;
    private final Environment environment;

    public TraceAutoConfiguration(TraceProperties traceProperties, Environment environment) {
        this.traceProperties = traceProperties;
        this.environment = environment;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (traceProperties.getOtel() == null) {
            return;
        }
        String serviceName = environment.getProperty("spring.application.name", "unknown-service");
        String args = OtelAgentArguments.generate(traceProperties.getOtel(), serviceName);
        log.info("\n\n[OTel Agent] 推荐的 JVM 启动参数:\njava {} \\\n    -jar your-app.jar\n", args);
    }
}
