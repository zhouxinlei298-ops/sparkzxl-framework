package com.github.sparkzxl.trace.provider;

import cn.hutool.core.util.IdUtil;
import org.springframework.web.server.ServerWebExchange;

public class LocalReactiveTraceIdProvider implements ReactiveTraceIdProvider {

    @Override
    public String traceId(ServerWebExchange exchange) {
        return IdUtil.fastSimpleUUID() + "." + IdUtil.getSnowflakeNextId();
    }
}
