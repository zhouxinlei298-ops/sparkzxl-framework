package com.github.sparkzxl.trace.config;

import com.github.sparkzxl.trace.properties.TraceProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@EnableConfigurationProperties(TraceProperties.class)
@ConditionalOnProperty(name = "sparkzxl.trace.enabled", havingValue = "true", matchIfMissing = true)
@Import({
        ServletTraceConfiguration.class,
        GatewayTraceConfiguration.class,
        FeignTraceConfiguration.class
})
public class TraceAutoConfiguration {
}
