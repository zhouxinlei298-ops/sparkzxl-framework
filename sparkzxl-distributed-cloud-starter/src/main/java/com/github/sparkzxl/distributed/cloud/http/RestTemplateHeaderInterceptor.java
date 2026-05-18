package com.github.sparkzxl.distributed.cloud.http;

import com.github.sparkzxl.core.constant.BaseContextConstants;
import com.github.sparkzxl.core.context.RequestLocalContextHolder;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
            BaseContextConstants.JWT_TOKEN_HEADER,
            "zone",
            "X-Real-IP",
            HttpHeaders.X_FORWARDED_FOR
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
