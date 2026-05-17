package com.github.sparkzxl.distributed.cloud.config;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.sparkzxl.distributed.cloud.http.RestTemplateHeaderInterceptor;
import com.github.sparkzxl.trace.interceptor.TraceRestTemplateHeaderInterceptor;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import lombok.AllArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.commons.httpclient.OkHttpClientConnectionPoolFactory;
import org.springframework.cloud.commons.httpclient.OkHttpClientFactory;
import org.springframework.cloud.openfeign.support.FeignHttpClientProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

/**
 * description: RestTemplate 相关的配置
 *
 * @author zhouxinlei
 */
@ConditionalOnClass(okhttp3.OkHttpClient.class)
@AllArgsConstructor
public class RestTemplateConfiguration {

    private static final Charset UTF_8 = StandardCharsets.UTF_8;
    private final ObjectMapper objectMapper;

    /**
     * 配置OkHttpClient
     */
    @Bean
    @ConditionalOnMissingBean(okhttp3.OkHttpClient.class)
    public okhttp3.OkHttpClient okHttp3Client(
            OkHttpClientFactory httpClientFactory,
            okhttp3.ConnectionPool connectionPool,
            FeignHttpClientProperties httpClientProperties) {
        Duration readTimeout = httpClientProperties.getOkHttp().getReadTimeout();
        return httpClientFactory.createBuilder(httpClientProperties.isDisableSslValidation())
                .followRedirects(httpClientProperties.isFollowRedirects())
                .readTimeout(readTimeout)
                .connectTimeout(httpClientProperties.getConnectionTimeout(), TimeUnit.MILLISECONDS)
                .connectionPool(connectionPool)
                .build();
    }

    /**
     * okhttp3 链接池配置
     */
    @Bean
    @ConditionalOnMissingBean(okhttp3.ConnectionPool.class)
    public okhttp3.ConnectionPool okHttp3ConnectionPool(FeignHttpClientProperties hcp,
            OkHttpClientConnectionPoolFactory connectionPoolFactory) {
        return connectionPoolFactory.create(hcp.getMaxConnections(), hcp.getTimeToLive(), hcp.getTimeToLiveUnit());
    }

    /**
     * 上下文 header 传播拦截器（非 traceId）
     */
    @Bean
    public RestTemplateHeaderInterceptor requestHeaderInterceptor() {
        return new RestTemplateHeaderInterceptor();
    }

    /**
     * 支持负载均衡的 LbRestTemplate, 传递请求头，一般用于内部 http 调用
     * 自动收集所有 ClientHttpRequestInterceptor（traceId 拦截器 + 上下文 header 拦截器）
     */
    @Bean("lbRestTemplate")
    @LoadBalanced
    @ConditionalOnMissingBean(RestTemplate.class)
    public RestTemplate lbRestTemplate(okhttp3.OkHttpClient httpClient,
                                       List<ClientHttpRequestInterceptor> interceptors) {
        RestTemplate lbRestTemplate = new RestTemplate(new OkHttp3ClientHttpRequestFactory(httpClient));
        lbRestTemplate.setInterceptors(interceptors);
        this.configMessageConverters(lbRestTemplate.getMessageConverters());
        return lbRestTemplate;
    }

    /**
     * 普通的 RestTemplate，不透传请求头，一般只做外部 http 调用
     */
    @Bean
    public RestTemplate restTemplate(okhttp3.OkHttpClient httpClient) {
        RestTemplate restTemplate = new RestTemplate(new OkHttp3ClientHttpRequestFactory(httpClient));
        this.configMessageConverters(restTemplate.getMessageConverters());
        return restTemplate;
    }

    private void configMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.removeIf(c -> c instanceof StringHttpMessageConverter || c instanceof MappingJackson2HttpMessageConverter);
        converters.add(new StringHttpMessageConverter(UTF_8));
        converters.add(new MappingJackson2HttpMessageConverter(this.objectMapper));
    }
}
