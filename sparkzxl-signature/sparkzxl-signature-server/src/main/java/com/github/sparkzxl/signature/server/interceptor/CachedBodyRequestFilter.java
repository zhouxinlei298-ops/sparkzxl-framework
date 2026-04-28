package com.github.sparkzxl.signature.server.interceptor;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 缓存请求 body 的过滤器，使 body 可被多次读取
 *
 * @author zhouxinlei
 */
public class CachedBodyRequestFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
        filterChain.doFilter(cachedRequest, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType == null) {
            return true;
        }
        return !StringUtils.startsWithIgnoreCase(contentType, MediaType.APPLICATION_JSON_VALUE)
                && !StringUtils.startsWithIgnoreCase(contentType, MediaType.APPLICATION_FORM_URLENCODED_VALUE);
    }
}
