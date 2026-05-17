package com.github.sparkzxl.trace.filter;

import com.github.sparkzxl.core.constant.BaseContextConstants;
import com.github.sparkzxl.core.constant.enums.RpcType;
import com.github.sparkzxl.core.context.RequestLocalContextHolder;
import com.github.sparkzxl.core.spring.SpringContextUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class ServletMdcFilter extends OncePerRequestFilter implements Ordered {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            RequestLocalContextHolder.set(BaseContextConstants.RPC_TYPE, RpcType.HTTP.getCode());
            String traceId = request.getHeader(BaseContextConstants.TRACE_ID_HEADER);
            if (StringUtils.isEmpty(traceId) || "N/A".equalsIgnoreCase(traceId)) {
                traceId = SpringContextUtils.getTraceId();
            }
            RequestLocalContextHolder.setTraceId(traceId);
            MDC.put(BaseContextConstants.LOG_TRACE_ID, traceId);

            String tenantId = request.getHeader(BaseContextConstants.TENANT_ID);
            if (StringUtils.isNotEmpty(tenantId)) {
                MDC.put(BaseContextConstants.TENANT_ID, tenantId);
            } else {
                MDC.put(BaseContextConstants.TENANT_ID, "N/A");
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
