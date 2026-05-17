package com.github.sparkzxl.trace.provider;

import cn.hutool.core.util.IdUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.skywalking.apm.toolkit.webflux.WebFluxSkyWalkingTraceContext;
import org.springframework.web.server.ServerWebExchange;

public class SkyWalkingReactiveTraceIdProvider implements ReactiveTraceIdProvider {

    @Override
    public String traceId(ServerWebExchange exchange) {
        String traceId = WebFluxSkyWalkingTraceContext.traceId(exchange);
        if (StringUtils.isBlank(traceId) || "N/A".equalsIgnoreCase(traceId)) {
            traceId = IdUtil.fastSimpleUUID() + "." + IdUtil.getSnowflakeNextId();
        }
        return traceId;
    }
}
