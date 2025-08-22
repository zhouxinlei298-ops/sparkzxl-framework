package com.github.sparkzxl.datasource.interceptor;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.github.sparkzxl.core.constant.BaseContextConstants;
import com.github.sparkzxl.core.context.RequestLocalContextHolder;
import com.github.sparkzxl.core.util.HttpRequestUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * description: 动态数据源拦截器
 *
 * @author zhoux
 */
@Slf4j
@RequiredArgsConstructor
public class DynamicDataSourceInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String tenantId = HttpRequestUtils.getHeader(request, BaseContextConstants.TENANT_ID);
        if (StringUtils.isEmpty(tenantId)) {
            tenantId = RequestLocalContextHolder.get(BaseContextConstants.TENANT_ID);
        }
        log.info("当前数据源:{}", tenantId);
        DynamicDataSourceContextHolder.push(tenantId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        DynamicDataSourceContextHolder.clear();
    }
}
