package com.github.sparkzxl.trace.filter;

import com.github.sparkzxl.core.constant.BaseContextConstants;
import com.github.sparkzxl.trace.properties.TraceProperties;
import com.github.sparkzxl.trace.provider.ReactiveTraceIdProvider;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

public class GatewayTraceFilter implements GlobalFilter, Ordered {

    private final ReactiveTraceIdProvider reactiveTraceIdProvider;
    private final TraceProperties traceProperties;

    public GatewayTraceFilter(ReactiveTraceIdProvider reactiveTraceIdProvider, TraceProperties traceProperties) {
        this.reactiveTraceIdProvider = reactiveTraceIdProvider;
        this.traceProperties = traceProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = reactiveTraceIdProvider.traceId(exchange);
        MDC.put(BaseContextConstants.LOG_TRACE_ID, traceId);

        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate()
                .header(BaseContextConstants.TRACE_ID_HEADER, traceId);

        // 传播上下文参数
        List<String> propagateHeaders = traceProperties.getPropagateHeaders();
        for (String headerName : propagateHeaders) {
            List<String> values = exchange.getRequest().getHeaders().get(headerName);
            if (values != null && !values.isEmpty()) {
                String value = values.get(0);
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
                    ctx = ctx.put(BaseContextConstants.LOG_TRACE_ID, traceId);
                    for (String headerName : propagateHeaders) {
                        List<String> values = exchange.getRequest().getHeaders().get(headerName);
                        if (values != null && !values.isEmpty()) {
                            ctx = ctx.put(headerName, values.get(0));
                        }
                    }
                    return ctx;
                })
                .doOnEach(signal -> {
                    String signalTraceId = signal.getContextView().getOrDefault(BaseContextConstants.LOG_TRACE_ID, "N/A");
                    MDC.put(BaseContextConstants.LOG_TRACE_ID, signalTraceId);
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
