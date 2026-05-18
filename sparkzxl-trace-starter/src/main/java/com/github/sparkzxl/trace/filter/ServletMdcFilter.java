package com.github.sparkzxl.trace.filter;

import com.github.sparkzxl.core.constant.BaseContextConstants;
import com.github.sparkzxl.core.constant.enums.RpcType;
import com.github.sparkzxl.core.context.RequestLocalContextHolder;
import com.github.sparkzxl.core.util.TraceContextUtils;
import com.github.sparkzxl.trace.properties.TraceProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class ServletMdcFilter extends OncePerRequestFilter implements Ordered {

    private final TraceProperties traceProperties;

    public ServletMdcFilter(TraceProperties traceProperties) {
        this.traceProperties = traceProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            RequestLocalContextHolder.set(BaseContextConstants.RPC_TYPE, RpcType.HTTP.getCode());

            MDC.put("trace_id", TraceContextUtils.getTraceId());

            List<String> propagateHeaders = traceProperties.getPropagateHeaders();
            for (String headerName : propagateHeaders) {
                String value = request.getHeader(headerName);
                if (value != null && !value.isEmpty()) {
                    MDC.put(headerName, value);
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
            RequestLocalContextHolder.remove();
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
