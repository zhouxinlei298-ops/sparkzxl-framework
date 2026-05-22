package com.github.sparkzxl.trace.filter;

import com.github.sparkzxl.core.constant.BaseContextConstants;
import com.github.sparkzxl.core.util.TraceContextUtils;
import com.github.sparkzxl.trace.properties.TraceProperties;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
public class GatewayTraceFilter implements GlobalFilter, Ordered {

    private final TraceProperties traceProperties;

    public GatewayTraceFilter(TraceProperties traceProperties) {
        this.traceProperties = traceProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = TraceContextUtils.getTraceId();
        MDC.put(BaseContextConstants.TRACE_ID, traceId);

        List<String> propagateHeaders = traceProperties.getPropagateHeaders();
        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate();
        for (String headerName : propagateHeaders) {
            List<String> values = exchange.getRequest().getHeaders().get(headerName);
            if (values != null && !values.isEmpty()) {
                String value = values.getFirst();
                if (value != null && !value.isEmpty()) {
                    MDC.put(headerName, value);
                    requestBuilder.header(headerName, value);
                }
            }
        }

        ServerHttpRequest mutatedRequest = requestBuilder.build();
        ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();

        return chain.filter(mutatedExchange)
                .contextWrite(ctx -> {
                    for (String headerName : propagateHeaders) {
                        List<String> values = exchange.getRequest().getHeaders().get(headerName);
                        if (values != null && !values.isEmpty()) {
                            ctx = ctx.put(headerName, values.getFirst());
                        }
                    }
                    return ctx;
                })
                .doOnEach(signal -> {
                    for (String headerName : propagateHeaders) {
                        String val = signal.getContextView().getOrDefault(headerName, "");
                        if (val != null && !val.isEmpty()) {
                            MDC.put(headerName, val);
                        }
                    }
                })
                .doFinally(signalType -> MDC.clear());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
