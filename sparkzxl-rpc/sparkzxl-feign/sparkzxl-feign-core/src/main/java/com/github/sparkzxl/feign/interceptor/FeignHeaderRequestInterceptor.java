package com.github.sparkzxl.feign.interceptor;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import com.github.sparkzxl.core.constant.BaseContextConstants;
import com.github.sparkzxl.core.context.RequestContextHelper;
import com.github.sparkzxl.core.context.RequestLocalContextHolder;
import com.github.sparkzxl.core.util.StrPool;
import com.github.sparkzxl.feign.properties.FeignProperties;
import com.google.common.net.HttpHeaders;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import io.seata.core.context.RootContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * description: feign client 拦截器， 实现将 feign 调用方的 请求头封装到 被调用方的请求头
 *
 * @author zhouxinlei
 */
@Slf4j
public class FeignHeaderRequestInterceptor implements RequestInterceptor {

    public static final List<String> HEADER_NAME_LIST = Arrays.asList(
            BaseContextConstants.TENANT_ID,
            BaseContextConstants.VERSION,
            BaseContextConstants.JWT_TOKEN_HEADER,
            BaseContextConstants.TRACE_ID_HEADER,
            "X-Real-IP",
            HttpHeaders.X_FORWARDED_FOR
    );
    private FeignProperties feignProperties;

    public FeignHeaderRequestInterceptor() {
    }

    @Autowired
    public void setFeignProperties(FeignProperties feignProperties) {
        this.feignProperties = feignProperties;
    }

    @Override
    public void apply(RequestTemplate template) {
        template.header(BaseContextConstants.REMOTE_CALL, StrPool.TRUE);
        if (feignProperties.getSeata().isEnabled()) {
            String xid = RootContext.getXID();
            log.info("当前XID：{}", xid);
            if (StrUtil.isNotEmpty(xid)) {
                template.header(RootContext.KEY_XID, xid);
            }
        }
        HttpServletRequest httpServletRequest = RequestContextHelper.getHttpServletRequestOrNull();
        if (httpServletRequest == null) {
            // 非 Web 上下文场景（异步线程、定时任务、消息消费者），使用 ThreadLocal 回退
            RequestLocalContextHolder.getLocalMap()
                    .forEach((key, value) -> template.header(key, URLUtil.encode(Convert.toStr(value))));
            return;
        }
        HEADER_NAME_LIST.forEach((headerName) -> {
            String header = httpServletRequest.getHeader(headerName);
            template.header(headerName, StringUtils.isEmpty(header) ? RequestLocalContextHolder.get(headerName) : header);
        });
        List<String> headerList = feignProperties.getInterceptor().getHeaderList();
        if (CollectionUtils.isNotEmpty(headerList)) {
            headerList.forEach((headerName) -> {
                String header = httpServletRequest.getHeader(headerName);
                template.header(headerName,
                        StringUtils.isEmpty(header) ? URLUtil.encode(RequestLocalContextHolder.get(headerName)) : header);
            });
        }
    }
}
