package com.github.sparkzxl.feign.interceptor;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import com.github.sparkzxl.core.constant.BaseContextConstants;
import com.github.sparkzxl.core.context.RequestContextHelper;
import com.github.sparkzxl.core.context.RequestLocalContextHolder;
import com.github.sparkzxl.core.util.HttpRequestUtils;
import com.github.sparkzxl.feign.properties.FeignProperties;
import com.google.common.net.HttpHeaders;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import io.seata.core.context.RootContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * description: feign client 拦截器， 实现将 feign 调用方的 请求头封装到 被调用方的请求头
 *
 * @author zhouxinlei
 */
@Slf4j
public class FeignHeaderRequestInterceptor implements RequestInterceptor {

    private static final Set<String> DEFAULT_PROPAGATE_HEADERS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            BaseContextConstants.TENANT_ID,
            BaseContextConstants.JWT_KEY_USER_ID,
            BaseContextConstants.JWT_KEY_ACCOUNT,
            BaseContextConstants.JWT_KEY_NAME,
            BaseContextConstants.VERSION,
            BaseContextConstants.TRACE_ID_HEADER,
            BaseContextConstants.JWT_TOKEN_HEADER,
            "zone",
            "X-Real-IP",
            HttpHeaders.X_FORWARDED_FOR
    )));

    private FeignProperties feignProperties;
    private Set<String> propagateHeaders;

    public FeignHeaderRequestInterceptor() {
    }

    @Autowired
    public void setFeignProperties(FeignProperties feignProperties) {
        this.feignProperties = feignProperties;
        this.propagateHeaders = new LinkedHashSet<>(DEFAULT_PROPAGATE_HEADERS);
        this.propagateHeaders.addAll(feignProperties.getInterceptor().getHeaderList());
    }

    @Override
    public void apply(RequestTemplate template) {
        if (feignProperties.getSeata().isEnabled()) {
            String xid = RootContext.getXID();
            log.debug("Feign拦截器加载，当前XID：{}", xid);
            if (StrUtil.isNotEmpty(xid)) {
                template.header(RootContext.KEY_XID, xid);
            }
        }
        HttpServletRequest request = RequestContextHelper.getHttpServletRequestOrNull();
        if (request == null) {
            RequestLocalContextHolder.getLocalMap().forEach((key, value) -> {
                String strValue = Convert.toStr(value);
                if (StrUtil.isNotEmpty(strValue)) {
                    template.header(key, URLUtil.encode(strValue, StandardCharsets.UTF_8));
                }
            });
            return;
        }
        propagateHeaders.forEach(headerName -> resolveAndEncodeHeader(request, template, headerName));
    }

    private void resolveAndEncodeHeader(HttpServletRequest request, RequestTemplate template, String headerName) {
        String headerValue = HttpRequestUtils.getHeader(request, headerName);
        if (StrUtil.isEmpty(headerValue)) {
            headerValue = RequestLocalContextHolder.get(headerName);
        }
        if (StrUtil.isNotEmpty(headerValue)) {
            template.header(headerName, URLUtil.encode(headerValue, StandardCharsets.UTF_8));
        }
    }
}
