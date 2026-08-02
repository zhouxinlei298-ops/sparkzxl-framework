package com.github.sparkzxl.boot.filter;

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

/**
 * Initializes and clears the request-scoped framework context and MDC.
 *
 * <p>HTTP header values are the initial context only. A later filter may replace
 * them with a trusted server-side context, such as a tenant resolved from an OAuth
 * interaction. The {@code finally} block always clears the holder so container
 * thread reuse cannot leak values into a subsequent request.</p>
 *
 * @author zhouxinlei
 * @since 2026-08-02
 */
public class BootContextFilter extends OncePerRequestFilter implements Ordered {

    private static final String[] REQUEST_CONTEXT_HEADERS = {
            BaseContextConstants.TENANT_ID,
            BaseContextConstants.VERSION
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            RequestLocalContextHolder.set(BaseContextConstants.RPC_TYPE, RpcType.HTTP.getCode());
            for (String header : REQUEST_CONTEXT_HEADERS) {
                RequestLocalContextHolder.set(header, request.getHeader(header));
            }

            String traceId = request.getHeader(BaseContextConstants.TRACE_ID_HEADER);
            if (StringUtils.isEmpty(traceId) || "N/A".equalsIgnoreCase(traceId)) {
                traceId = SpringContextUtils.getTraceId();
            }
            RequestLocalContextHolder.setTraceId(traceId);
            MDC.put(BaseContextConstants.LOG_TRACE_ID, traceId);

            String tenantId = request.getHeader(BaseContextConstants.TENANT_ID);
            MDC.put(BaseContextConstants.TENANT_ID,
                    StringUtils.isNotEmpty(tenantId) ? tenantId : "N/A");
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
