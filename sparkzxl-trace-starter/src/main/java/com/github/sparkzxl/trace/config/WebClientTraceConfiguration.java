package com.github.sparkzxl.trace.config;

import com.github.sparkzxl.trace.filter.WebClientTraceExchangeFilterFunction;
import com.github.sparkzxl.trace.properties.TraceProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;

@Configuration
@ConditionalOnClass(name = "org.springframework.web.reactive.function.client.WebClient")
public class WebClientTraceConfiguration {

    @Bean
    public WebClientCustomizer traceWebClientCustomizer(TraceProperties traceProperties) {
        ExchangeFilterFunction filter = new WebClientTraceExchangeFilterFunction(traceProperties.getPropagateHeaders());
        return builder -> builder.filter(filter);
    }
}
