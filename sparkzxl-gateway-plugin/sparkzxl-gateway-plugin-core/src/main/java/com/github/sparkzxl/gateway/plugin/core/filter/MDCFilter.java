package com.github.sparkzxl.gateway.plugin.core.filter;

import cn.hutool.core.util.IdUtil;
import com.github.sparkzxl.core.constant.BaseContextConstants;
import com.github.sparkzxl.gateway.utils.HostAddressUtils;
import com.github.sparkzxl.gateway.utils.ReactorHttpHelper;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * description: MDC上下文注入
 *
 * @author zhouxinlei
 * @since 2022-01-10 11:31:43
 */
public class MDCFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // 设置租户ID
        String tenantId = ReactorHttpHelper.getHeader(request, BaseContextConstants.TENANT_ID);
        MDC.put(BaseContextConstants.TENANT_ID, tenantId);
        // 生成traceId
        String traceId = IdUtil.fastSimpleUUID() + "." + IdUtil.getSnowflakeNextId();
        // 调试日志
        System.out.println("生成traceId: " + traceId + ", 线程: " + Thread.currentThread().getName());

        // 将traceId放入请求头
        ServerHttpRequest serverHttpRequest = exchange.getRequest().mutate()
                .headers(h -> h.add(BaseContextConstants.TRACE_ID_HEADER, traceId))
                .build();
        ServerWebExchange build = exchange.mutate().request(serverHttpRequest).build();
        MDC.put(BaseContextConstants.TRACE_ID_HEADER, traceId);
        return chain.filter(build);
    }

    @Override
    public int getOrder() {
        return Integer.MIN_VALUE;
    }
}
