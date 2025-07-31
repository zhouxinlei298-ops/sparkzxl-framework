package com.github.sparkzxl.signature.server.filter;

import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.github.sparkzxl.core.constant.BaseContextConstants;
import com.github.sparkzxl.core.json.JsonUtils;
import com.github.sparkzxl.core.support.ArgumentException;
import com.github.sparkzxl.core.util.ArgumentAssert;
import com.github.sparkzxl.core.util.StrPool;
import com.github.sparkzxl.signature.constant.SignatureConstant;
import com.github.sparkzxl.signature.executor.SignatureExecutor;
import com.github.sparkzxl.signature.executor.SignatureExecutorContext;
import com.github.sparkzxl.signature.properties.SignatureProperties;
import com.github.sparkzxl.signature.server.cache.SignCache;
import com.github.sparkzxl.signature.server.properties.SignatureServerProperties;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.rewrite.CachedBodyOutputMessage;
import org.springframework.cloud.gateway.support.BodyInserterContext;
import org.springframework.core.Ordered;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.MultipartHttpMessageReader;
import org.springframework.http.codec.multipart.Part;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * description: 验签过滤器
 *
 * @author zhouxinlei
 * @since 2024-05-21 11:03:48
 */
@Slf4j
public class SignAuthFilter implements GlobalFilter, Ordered {

    @Autowired
    private SignatureExecutorContext signatureExecutorContext;
    @Autowired
    private SignCache signCache;
    @Autowired
    private SignatureProperties signatureProperties;
    @Autowired
    private SignatureServerProperties signatureServerProperties;
    private final MultipartHttpMessageReader multipartReader;

    private static final AntPathMatcher ANT_PATH_MATCHER = new AntPathMatcher();

    public SignAuthFilter(MultipartHttpMessageReader multipartReader) {
        this.multipartReader = multipartReader;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        List<String> excludePatterns = signatureServerProperties.getExcludePatterns();
        boolean matched = excludePatterns.stream().anyMatch(url -> ANT_PATH_MATCHER.match(url, path)
                || ANT_PATH_MATCHER.matchStart(url, path));
        if (matched) {
            return chain.filter(exchange);
        }
        List<String> includePatterns = signatureServerProperties.getIncludePatterns();
        boolean includeMatched = includePatterns.stream().anyMatch(url -> ANT_PATH_MATCHER.match(url, path)
                || ANT_PATH_MATCHER.matchStart(url, path));
        if (!includeMatched) {
            return chain.filter(exchange);
        }
        List<SignatureProperties.AppProperties> propertiesConfigs = signatureProperties.getConfigs();
        if (CollectionUtils.isEmpty(propertiesConfigs)) {
            return chain.filter(exchange);
        }
        ServerHttpRequest request = exchange.getRequest();
        String tenantId = request.getHeaders().getFirst(BaseContextConstants.TENANT_ID);
        Optional<SignatureProperties.AppProperties> propertiesOptional = propertiesConfigs.stream().filter(x -> x.getTenantId().equals(tenantId)).findFirst();
        if (!propertiesOptional.isPresent()) {
            return chain.filter(exchange);
        }
        String contentType = request.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
        // 获取时间戳
        String appKey = request.getHeaders().getFirst(SignatureConstant.APP_KEY);
        // 获取时间戳
        String timestamp = request.getHeaders().getFirst(SignatureConstant.TIMESTAMP);
        // 获取随机字符串
        String nonce = request.getHeaders().getFirst(SignatureConstant.NONCE);
        // 获取签名
        String signature = request.getHeaders().getFirst(SignatureConstant.SIGNATURE);
        if (StrUtil.isEmpty(appKey)) {
            throw new ArgumentException("invalid appKey");
        }

        // 判断时间是否大于xx秒(防止重放攻击)
        if (StrUtil.isEmpty(timestamp) || DateUtil.between(DateUtil.date(Long.parseLong(timestamp)), DateUtil.date(), DateUnit.SECOND) > signatureServerProperties.getNonceTimeoutSeconds()) {
            throw new ArgumentException("invalid timestamp");
        }

        // 判断该用户的nonce参数是否已经在redis中（防止短时间内的重放攻击）
        boolean haveNonce = signCache.containsKey(nonce);
        if (StrUtil.isEmpty(nonce) || haveNonce) {
            throw new ArgumentException("invalid nonce");
        }

        // 对请求头参数进行签名
        if (StrUtil.isEmpty(signature)) {
            throw new ArgumentException("invalid signature");
        }
        if (StringUtils.startsWithIgnoreCase(contentType, MediaType.APPLICATION_JSON_VALUE)
                || StringUtils.startsWithIgnoreCase(contentType, MediaType.MULTIPART_FORM_DATA_VALUE)) {
            return readBody(signature, appKey, timestamp, nonce, exchange, chain);
        }
        if (MediaType.APPLICATION_FORM_URLENCODED_VALUE.equals(contentType)) {
            return readFormData(signature, appKey, timestamp, nonce, exchange, chain);
        } else {
            boolean verified = this.verifySignature(exchange, signature, appKey, timestamp, nonce, null);
            if (!verified) {
                return Mono.error(new ArgumentException("验签失败"));
            }
        }
        return chain.filter(exchange);
    }

    private Mono<Void> readFormData(String signature, String appKey, String timestamp, String nonce, ServerWebExchange exchange, GatewayFilterChain chain) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        return exchange.getFormData()
                .doOnNext(x -> {
                    if (MapUtil.isNotEmpty(x)) {
                        formData.putAll(x);
                    }
                })
                .then(Mono.defer(() -> {
                    Charset charset = Objects.requireNonNull(headers.getContentType()).getCharset();
                    charset = charset == null ? StandardCharsets.UTF_8 : charset;
                    String charsetName = charset.name();
                    /*
                     * formData is empty just return
                     */
                    if (formData.isEmpty()) {
                        return chain.filter(exchange);
                    }
                    StringBuilder formDataBodyBuilder = new StringBuilder();
                    Map<String, Object> formDataBodyMap = Maps.newConcurrentMap();
                    String entryKey;
                    List<String> entryValue;
                    try {
                        /*
                         * repackage form data
                         */
                        for (Map.Entry<String, List<String>> entry : formData.entrySet()) {
                            entryKey = entry.getKey();
                            entryValue = entry.getValue();
                            if (entryValue.size() > 1) {
                                for (String value : entryValue) {
                                    formDataBodyBuilder.append(entryKey).append("=").append(URLEncoder.encode(value, charsetName))
                                            .append("&");
                                }
                            } else {
                                formDataBodyBuilder.append(entryKey).append("=").append(URLEncoder.encode(entryValue.get(0), charsetName))
                                        .append("&");
                            }
                            formDataBodyMap.put(entryKey, entryValue.get(0));
                        }
                    } catch (UnsupportedEncodingException ignored) {
                    }
                    /*
                     * substring with the last char '&'
                     */
                    String formDataBodyString = "";
                    if (formDataBodyBuilder.length() > 0) {
                        formDataBodyString = formDataBodyBuilder.substring(0, formDataBodyBuilder.length() - 1);
                    }
                    boolean verified = this.verifySignature(exchange, signature, appKey, timestamp, nonce, JsonUtils.getJson().toJson(formDataBodyMap));
                    if (!verified) {
                        return Mono.error(new ArgumentException("验签失败"));
                    }

                    /*
                     * get data bytes
                     */
                    byte[] bodyBytes = formDataBodyString.getBytes(charset);

                    int contentLength = bodyBytes.length;
                    HttpHeaders httpHeaders = new HttpHeaders();
                    httpHeaders.putAll(exchange.getRequest().getHeaders());
                    httpHeaders.remove(HttpHeaders.CONTENT_LENGTH);
                    /*
                     * in case of content-length not matched
                     */
                    httpHeaders.setContentLength(contentLength);
                    /*
                     * use BodyInserter to InsertFormData Body
                     */
                    BodyInserter<String, ReactiveHttpOutputMessage> bodyInserter = BodyInserters.fromValue(formDataBodyString);
                    CachedBodyOutputMessage cachedBodyOutputMessage = new CachedBodyOutputMessage(exchange, httpHeaders);
                    return bodyInserter.insert(cachedBodyOutputMessage, new BodyInserterContext())
                            .then(Mono.defer(() -> {
                                ServerHttpRequestDecorator decorator = new ServerHttpRequestDecorator(
                                        exchange.getRequest()) {
                                    @Override
                                    public HttpHeaders getHeaders() {
                                        return httpHeaders;
                                    }

                                    @Override
                                    public Flux<DataBuffer> getBody() {
                                        return cachedBodyOutputMessage.getBody();
                                    }
                                };
                                return chain.filter(exchange.mutate().request(decorator).build());
                            }));
                }));
    }


    /**
     * ReadJsonBody
     *
     * @param exchange exchange
     * @param chain    chain
     * @return Mono<Void>
     */
    private Mono<Void> readBody(String signature, String appKey, String timestamp, String nonce, ServerWebExchange exchange, GatewayFilterChain chain) {
        // 判断是否为文件上传请求
        boolean isMultipart = isMultipartRequest(exchange);

        if (isMultipart) {
            // 处理文件上传请求，提取文件名进行验签
            return handleMultipartRequest(signature, appKey, timestamp, nonce, exchange, chain);
        }
        return DataBufferUtils.join(exchange.getRequest().getBody())
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    String requestData = new String(bytes, StandardCharsets.UTF_8);
                    boolean verified = this.verifySignature(exchange, signature, appKey, timestamp, nonce, requestData);
                    if (!verified) {
                        return Mono.error(new ArgumentException("验签失败"));
                    }
                    Flux<DataBuffer> cachedFlux = Flux.defer(() -> {
                        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
                        DataBufferUtils.retain(buffer);
                        return Mono.just(buffer);
                    });
                    ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
                        @Override
                        public Flux<DataBuffer> getBody() {
                            return cachedFlux;
                        }
                    };
                    // 将本次用户请求的nonceStr参数存到redis中设置xx秒后自动删除
                    signCache.set(nonce, nonce, signatureServerProperties.getNonceTimeoutSeconds());
                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                });
    }


    /**
     * 判断是否为multipart/form-data请求（通常用于文件上传）
     */
    private boolean isMultipartRequest(ServerWebExchange exchange) {
        String contentType = exchange.getRequest().getHeaders().getFirst("Content-Type");
        if (contentType == null) {
            return false;
        }
        // 检查Content-Type是否以multipart/form-data开头
        return contentType.startsWith("multipart/form-data");
    }


    private Mono<Void> handleMultipartRequest(String signature, String appKey, String timestamp, String nonce,
                                              ServerWebExchange exchange, GatewayFilterChain chain) {
        // 校验请求头是否包含 boundary
        if (!hasValidMultipartBoundary(exchange)) {
            return Mono.error(new ArgumentException("文件上传请求缺少 boundary 参数"));
        }
        // 1. 缓存原始请求体（避免二次消费）
        return DataBufferUtils.join(exchange.getRequest().getBody())
                .flatMap(originalDataBuffer -> {
                    // 复制原始字节数据
                    byte[] originalBytes = new byte[originalDataBuffer.readableByteCount()];
                    originalDataBuffer.read(originalBytes);
                    DataBufferUtils.release(originalDataBuffer);

                    // 2. 重建请求体，供 multipart 解析器使用
                    DataBuffer cachedBuffer = exchange.getResponse().bufferFactory().wrap(originalBytes);
                    Flux<DataBuffer> cachedBody = Flux.just(cachedBuffer);

                    // 包装请求，使用缓存的 body
                    ServerHttpRequest wrappedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
                        @Override
                        public Flux<DataBuffer> getBody() {
                            return cachedBody;
                        }
                    };

                    // 3. 使用 MultipartHttpMessageReader 解析（依赖正确的 boundary）
                    // 修复：创建空的hints map而不是null
                    Map<String, Object> hints = Collections.emptyMap();
                    return multipartReader.read(
                                    ResolvableType.forClass(MultiValueMap.class),
                                    wrappedRequest,
                                    hints
                            )
                            .next() // 获取第一个 MultiValueMap（单个请求通常只有一个）
                            .flatMap(partsMap -> {
                                // 存储表单字段名与文件名的映射关系 (fieldName -> filename)
                                Map<String, String> fileDataMap = new HashMap<>();

                                // 遍历MultiValueMap的entry集，获取key（表单字段名）和对应的Part
                                for (Map.Entry<String, List<Part>> entry : partsMap.entrySet()) {
                                    // 表单字段名（如<input name="file1">中的"file1"）
                                    String fieldName = entry.getKey();
                                    List<Part> parts = entry.getValue();
                                    // 存储当前字段的所有文件名
                                    List<String> fieldFilenames = new ArrayList<>();
                                    for (Part part : parts) {
                                        if (part instanceof FilePart) {
                                            // 处理文件类型字段
                                            String filename = ((FilePart) part).filename();
                                            if (StringUtils.isNotBlank(filename)) {
                                                fieldFilenames.add(filename.trim());
                                            }
                                        } else {
                                            // 处理普通表单字段（读取文本内容）
                                            StringBuilder fieldContent = new StringBuilder();
                                            part.content()
                                                    .map(dataBuffer -> {
                                                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                                                        dataBuffer.read(bytes);
                                                        DataBufferUtils.release(dataBuffer);
                                                        return new String(bytes, StandardCharsets.UTF_8);
                                                    })
                                                    .subscribe(content -> fieldContent.append(content));

                                            // 存储普通字段内容
                                            fileDataMap.put(fieldName, fieldContent.toString());
                                        }
                                    }
                                    // 对文件名进行排序（按自然顺序，如字母顺序、数字顺序）
                                    Collections.sort(fieldFilenames);
                                    // 将排序后的文件名用逗号拼接后存入Map
                                    if (!fieldFilenames.isEmpty()) {
                                        String joinedFilenames = String.join(",", fieldFilenames);
                                        fileDataMap.put(fieldName, joinedFilenames);
                                    }
                                }
                                // 释放所有 Part 的内容流
                                Flux<Part> allParts = Flux.fromIterable(partsMap.values())
                                        .flatMap(Flux::fromIterable);
                                return allParts
                                        .flatMap(part -> part.content()
                                                .doOnNext(DataBufferUtils::release)
                                                .then()
                                        )
                                        // 4. 验签逻辑
                                        .then(Mono.fromRunnable(() -> {
                                            String fileJsonData = JsonUtils.getJson().toJson(fileDataMap);
                                            boolean verified = verifySignature(exchange, signature, appKey, timestamp, nonce, fileJsonData);
                                            if (!verified) {
                                                throw new ArgumentException("文件名验签失败: " + fileJsonData);
                                            }
                                        }))
                                        // 5. 重建请求供后续处理
                                        .then(Mono.defer(() -> {
                                            Flux<DataBuffer> finalBody = Flux.defer(() -> {
                                                DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(originalBytes);
                                                DataBufferUtils.retain(buffer);
                                                return Mono.just(buffer);
                                            });

                                            ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
                                                @Override
                                                public Flux<DataBuffer> getBody() {
                                                    return finalBody;
                                                }
                                            };

                                            signCache.set(nonce, nonce, signatureServerProperties.getNonceTimeoutSeconds());
                                            return chain.filter(exchange.mutate().request(mutatedRequest).build());
                                        }));
                            })
                            .onErrorResume(e -> {
                                // 捕获 boundary 解析失败的异常
                                String errorMsg = "文件上传解析失败（可能是 boundary 不匹配）: " + e.getMessage();
                                return Mono.error(new ArgumentException(errorMsg));
                            });
                });
    }

    // 辅助方法：校验 boundary 是否存在
    private boolean hasValidMultipartBoundary(ServerWebExchange exchange) {
        String contentType = exchange.getRequest().getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
        if (contentType == null || !contentType.startsWith(MediaType.MULTIPART_FORM_DATA_VALUE)) {
            return false;
        }
        return contentType.contains("boundary=");
    }

    /**
     * 校验签名是否一致
     *
     * @param signature 请求的sign
     * @param appKey    应用ID
     * @param timestamp 时间戳
     * @param nonce     随机值
     * @param bodyData  请求体数据
     * @return boolean
     */
    private boolean verifySignature(ServerWebExchange exchange, String signature, String appKey, String timestamp, String nonce, String bodyData) {
        Map<String, Object> map = new HashMap<>();
        // 添加URL参数
        MultiValueMap<String, String> queryParams = exchange.getRequest().getQueryParams();
        for (Map.Entry<String, List<String>> entry : queryParams.entrySet()) {
            // 排除签名参数
            if (!SignatureConstant.SIGNATURE.equalsIgnoreCase(entry.getKey())) {
                map.put(entry.getKey(), entry.getValue());
            }
        }
        if (StringUtils.isNotEmpty(bodyData)) {
            List<Map<String, Object>> requestBodyList;
            if (StrUtil.startWith(bodyData, StrPool.LEFT_SQ_BRACKET)) {
                // 处理JSON数组
                requestBodyList = JsonUtils.getJson().toJavaList(bodyData, new TypeReference<Map<String, Object>>() {
                    @Override
                    public Type getType() {
                        return super.getType();
                    }
                });
                map.put("body", requestBodyList);
            } else {
                // 处理单个JSON对象
                Map<String, Object> requestBodyMap = JsonUtils.getJson().toMap(bodyData);
                map.put("body", requestBodyMap);
            }
        }
        log.debug("验签请求参数:{}", JsonUtils.getJson().toJson(map));
        String tenantId = exchange.getRequest().getHeaders().getFirst(BaseContextConstants.TENANT_ID);
        Map<String, SignatureProperties.AppProperties> provider = signatureProperties.getConfigMap();
        SignatureProperties.AppProperties properties = provider.get(tenantId);
        ArgumentAssert.notNull(properties, "应用程序ID[{}]签名配置不存在", tenantId);
        ArgumentAssert.isTrue(properties.getAppKey().equals(appKey), "appKey不一致，无效请求");
        SignatureExecutor signatureExecutor = signatureExecutorContext.getExecutor(properties.getSignType().name());
        return signatureExecutor.verify(tenantId, appKey, Long.valueOf(timestamp), nonce, signature, map);
    }

    @Override
    public int getOrder() {
        return 1;
    }
}
