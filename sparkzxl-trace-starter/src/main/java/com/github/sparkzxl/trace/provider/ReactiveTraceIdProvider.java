package com.github.sparkzxl.trace.provider;

import org.springframework.web.server.ServerWebExchange;

public interface ReactiveTraceIdProvider {

    String traceId(ServerWebExchange exchange);
}
