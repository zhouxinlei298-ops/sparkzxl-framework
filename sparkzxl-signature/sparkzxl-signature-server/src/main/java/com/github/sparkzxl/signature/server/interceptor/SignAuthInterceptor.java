package com.github.sparkzxl.signature.server.interceptor;

import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.StrUtil;
import com.github.sparkzxl.core.constant.BaseContextConstants;
import com.github.sparkzxl.core.json.JsonUtils;
import com.github.sparkzxl.core.support.ArgumentException;
import com.github.sparkzxl.core.util.StrPool;
import com.github.sparkzxl.signature.constant.SignatureConstant;
import com.github.sparkzxl.signature.server.method.SignProcessor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 签名拦截器（Spring MVC 环境）
 *
 * @author zhouxinlei
 * @since 2025-06-18 16:53:07
 */
public class SignAuthInterceptor implements AsyncHandlerInterceptor {

    @Autowired
    private SignProcessor signProcessor;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String tenantId = request.getHeader(BaseContextConstants.TENANT_ID);
        boolean checked = signProcessor.check(tenantId);
        if (!checked) {
            return true;
        }

        String appKey = request.getHeader(SignatureConstant.APP_KEY);
        String timestamp = request.getHeader(SignatureConstant.TIMESTAMP);
        String nonce = request.getHeader(SignatureConstant.NONCE);
        String signature = request.getHeader(SignatureConstant.SIGNATURE);

        String bodyData = resolveBodyData(request);
        Map<String, Object> params = buildSignParams(request, bodyData);

        if (StrUtil.isEmpty(signature) || !signProcessor.verifySign(tenantId, appKey, timestamp, nonce, signature, params)) {
            throw new ArgumentException("验签失败");
        }
        return true;
    }

    /**
     * 根据Content-Type读取请求体数据
     */
    private String resolveBodyData(HttpServletRequest request) throws Exception {
        String contentType = request.getContentType();
        if (contentType == null) {
            return null;
        }
        if (StringUtils.startsWithIgnoreCase(contentType, MediaType.APPLICATION_JSON_VALUE)) {
            return readJsonBody(request);
        }
        if (StringUtils.startsWithIgnoreCase(contentType, MediaType.MULTIPART_FORM_DATA_VALUE)) {
            return readMultipartData(request);
        }
        if (StringUtils.startsWithIgnoreCase(contentType, MediaType.APPLICATION_FORM_URLENCODED_VALUE)) {
            return readFormData(request);
        }
        return null;
    }

    /**
     * 读取JSON请求体
     */
    private String readJsonBody(HttpServletRequest request) {
        if (request instanceof CachedBodyHttpServletRequest) {
            CachedBodyHttpServletRequest cachedRequest = (CachedBodyHttpServletRequest) request;
            return new String(cachedRequest.getCachedBody(), StandardCharsets.UTF_8);
        }
        return null;
    }

    /**
     * 解析multipart请求，提取文件名和表单字段值
     */
    private String readMultipartData(HttpServletRequest request) throws Exception {
        Collection<Part> parts = request.getParts();
        Map<String, Object> dataMap = new HashMap<>();
        Map<String, List<String>> fileNamesMap = new HashMap<>();

        for (Part part : parts) {
            String fieldName = part.getName();
            String submittedFileName = part.getSubmittedFileName();
            if (submittedFileName != null && !submittedFileName.isEmpty()) {
                // 文件类型：提取文件名，多文件按字段名分组
                String fileName = FileNameUtil.getName(submittedFileName).trim();
                fileNamesMap.computeIfAbsent(fieldName, k -> new ArrayList<>()).add(fileName);
            } else {
                // 普通表单字段：读取文本内容
                String value = new String(StreamUtils.copyToByteArray(part.getInputStream()), StandardCharsets.UTF_8);
                dataMap.put(fieldName, value);
            }
        }

        // 文件名排序后逗号拼接
        for (Map.Entry<String, List<String>> entry : fileNamesMap.entrySet()) {
            Collections.sort(entry.getValue());
            dataMap.put(entry.getKey(), String.join(",", entry.getValue()));
        }

        if (dataMap.isEmpty()) {
            return null;
        }
        return JsonUtils.getJson().toJson(dataMap);
    }

    /**
     * 解析form-urlencoded请求体，排除URL query参数后返回表单数据JSON
     */
    private String readFormData(HttpServletRequest request) {
        Set<String> urlParamNames = parseQueryString(request.getQueryString()).keySet();

        Map<String, Object> formDataMap = new HashMap<>();
        Map<String, String[]> parameterMap = request.getParameterMap();
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            if (urlParamNames.contains(entry.getKey())) {
                continue;
            }
            String[] values = entry.getValue();
            formDataMap.put(entry.getKey(), values[0]);
        }

        if (formDataMap.isEmpty()) {
            return null;
        }
        return JsonUtils.getJson().toJson(formDataMap);
    }

    /**
     * 解析URL query string为参数Map，排除signature参数
     */
    private Map<String, String> parseQueryString(String queryString) {
        Map<String, String> params = new HashMap<>();
        if (StrUtil.isEmpty(queryString)) {
            return params;
        }
        String[] pairs = queryString.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx < 0) {
                continue;
            }
            String name = pair.substring(0, idx);
            if (SignatureConstant.SIGNATURE.equalsIgnoreCase(name)) {
                continue;
            }
            String value = pair.substring(idx + 1);
            params.put(name, value);
        }
        return params;
    }

    /**
     * 构建完整的验签参数：URL query参数 + body数据
     */
    private Map<String, Object> buildSignParams(HttpServletRequest request, String bodyData) {

        // 添加URL query参数（排除signature）
        Map<String, String> urlParams = parseQueryString(request.getQueryString());
        Map<String, Object> map = new HashMap<>(urlParams);

        // 添加body数据
        if (StringUtils.isNotEmpty(bodyData)) {
            if (StrUtil.startWith(bodyData, StrPool.LEFT_SQ_BRACKET)) {
                List<Map<String, Object>> requestBodyList = JsonUtils.getJson().toJavaList(bodyData, new TypeReference<Map<String, Object>>() {
                    @Override
                    public Type getType() {
                        return super.getType();
                    }
                });
                map.put("body", requestBodyList);
            } else {
                Map<String, Object> requestBodyMap = JsonUtils.getJson().toMap(bodyData);
                map.put("body", requestBodyMap);
            }
        }
        return map;
    }
}
