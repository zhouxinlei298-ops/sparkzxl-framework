package com.github.sparkzxl.trace.interceptor;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import com.github.sparkzxl.core.context.RequestContextHelper;
import com.github.sparkzxl.core.context.RequestLocalContextHolder;
import com.github.sparkzxl.trace.properties.TraceProperties;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;

@Slf4j
public class TraceFeignRequestInterceptor implements RequestInterceptor {

    private final TraceProperties traceProperties;

    public TraceFeignRequestInterceptor(TraceProperties traceProperties) {
        this.traceProperties = traceProperties;
    }

    @Override
    public void apply(RequestTemplate template) {
        // 传播业务上下文参数（traceId 由 OTel Agent 自动传播 W3C traceparent）
        HttpServletRequest servletRequest = RequestContextHelper.getHttpServletRequestOrNull();
        for (String headerName : traceProperties.getPropagateHeaders()) {
            String headerValue = resolveValue(servletRequest, headerName);
            if (StrUtil.isNotEmpty(headerValue)) {
                template.header(headerName, URLUtil.encode(headerValue, StandardCharsets.UTF_8));
            }
        }
    }

    private String resolveValue(HttpServletRequest servletRequest, String headerName) {
        if (servletRequest != null) {
            String value = servletRequest.getHeader(headerName);
            if (StrUtil.isNotEmpty(value)) {
                return value;
            }
        }
        return RequestLocalContextHolder.get(headerName);
    }
}
