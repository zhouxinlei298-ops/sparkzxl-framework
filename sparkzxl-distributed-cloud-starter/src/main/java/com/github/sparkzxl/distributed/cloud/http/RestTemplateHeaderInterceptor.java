package com.github.sparkzxl.distributed.cloud.http;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import com.github.sparkzxl.core.constant.BaseContextConstants;
import com.github.sparkzxl.core.context.RequestContextHelper;
import com.github.sparkzxl.core.context.RequestLocalContextHolder;
import com.github.sparkzxl.core.util.HttpRequestUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * description: 通过 RestTemplate 调用时，传递请求头和线程变量
 *
 * @author zhouxinlei
 */
@AllArgsConstructor
@Slf4j
public class RestTemplateHeaderInterceptor implements ClientHttpRequestInterceptor {

    private static final Set<String> PROPAGATE_HEADERS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            BaseContextConstants.TENANT_ID,
            BaseContextConstants.JWT_KEY_USER_ID,
            BaseContextConstants.JWT_KEY_ACCOUNT,
            BaseContextConstants.JWT_KEY_NAME,
            BaseContextConstants.VERSION,
            BaseContextConstants.TRACE_ID_HEADER,
            BaseContextConstants.JWT_TOKEN_HEADER,
            "zone",
            "X-Real-IP",
            com.google.common.net.HttpHeaders.X_FORWARDED_FOR
    )));

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] bytes,
                                        ClientHttpRequestExecution execution) throws IOException {
        HttpServletRequest servletRequest = RequestContextHelper.getHttpServletRequestOrNull();
        if (servletRequest == null) {
            PROPAGATE_HEADERS.forEach(headerName -> {
                String headerValue = RequestLocalContextHolder.get(headerName);
                if (StrUtil.isNotEmpty(headerValue)) {
                    request.getHeaders().add(headerName, URLUtil.encode(headerValue, StandardCharsets.UTF_8));
                }
            });
            return execution.execute(request, bytes);
        }
        PROPAGATE_HEADERS.forEach(headerName -> resolveAndEncodeHeader(servletRequest, request, headerName));
        return execution.execute(request, bytes);
    }

    private void resolveAndEncodeHeader(HttpServletRequest servletRequest, HttpRequest request, String headerName) {
        String headerValue = HttpRequestUtils.getHeader(servletRequest, headerName);
        if (StrUtil.isEmpty(headerValue)) {
            headerValue = RequestLocalContextHolder.get(headerName);
        }
        if (StrUtil.isNotEmpty(headerValue)) {
            request.getHeaders().add(headerName, URLUtil.encode(headerValue, StandardCharsets.UTF_8));
        }
    }
}
