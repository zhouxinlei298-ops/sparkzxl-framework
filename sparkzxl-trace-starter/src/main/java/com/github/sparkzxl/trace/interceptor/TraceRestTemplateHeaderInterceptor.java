package com.github.sparkzxl.trace.interceptor;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import com.github.sparkzxl.core.context.RequestContextHelper;
import com.github.sparkzxl.core.context.RequestLocalContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
public class TraceRestTemplateHeaderInterceptor implements ClientHttpRequestInterceptor {

    private static final String TRACE_ID_HEADER = "traceId";

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] bytes,
                                        ClientHttpRequestExecution execution) throws IOException {
        String traceId = RequestLocalContextHolder.traceId();
        if (StrUtil.isNotEmpty(traceId) && !"N/A".equals(traceId)) {
            request.getHeaders().add(TRACE_ID_HEADER, URLUtil.encode(traceId, StandardCharsets.UTF_8));
        }
        return execution.execute(request, bytes);
    }
}
