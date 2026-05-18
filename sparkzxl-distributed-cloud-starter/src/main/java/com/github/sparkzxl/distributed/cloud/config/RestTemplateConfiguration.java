package com.github.sparkzxl.distributed.cloud.config;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.sparkzxl.distributed.cloud.http.RestTemplateHeaderInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * description: RestTemplate 配置 - 使用 Apache HttpClient 5 (官方推荐)
 *
 * <p>Spring Framework 6+ (Spring Boot 3.x) 不再支持 Apache HttpClient 4，
 * 必须使用 Apache HttpClient 5</p>
 *
 * @author zhouxinlei
 * @since 2026-04-11
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(RestTemplateProperties.class)
@RequiredArgsConstructor
public class RestTemplateConfiguration {

    private final RestTemplateProperties restTemplateProperties;
    private final ObjectMapper objectMapper;

    /**
     * RestTemplate Bean
     */
    @Bean
    @ConditionalOnMissingBean(RestTemplate.class)
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate(httpClientComponentsClientHttpRequestFactory());
        configMessageConverters(restTemplate.getMessageConverters());
        return restTemplate;
    }

    /**
     * 负载均衡 RestTemplate Bean
     */
    @Bean("lbRestTemplate")
    @LoadBalanced
    @ConditionalOnMissingBean(name = "lbRestTemplate")
    public RestTemplate lbRestTemplate(List<ClientHttpRequestInterceptor> interceptors) {
        RestTemplate lbRestTemplate = new RestTemplate(httpClientComponentsClientHttpRequestFactory());
        lbRestTemplate.setInterceptors(interceptors);
        configMessageConverters(lbRestTemplate.getMessageConverters());
        return lbRestTemplate;
    }

    /**
     * RestTemplate
     * 普通的 RestTemplate，不透传请求头，一般只做外部 http 调用
     */
    @Bean
    @ConditionalOnMissingBean(RestTemplateHeaderInterceptor.class)
    public RestTemplateHeaderInterceptor requestHeaderInterceptor() {
        return new RestTemplateHeaderInterceptor();
    }

    /**
     * HttpComponentsClientHttpRequestFactory Bean
     * 使用 Apache HttpClient 5
     */
    @Bean
    public HttpComponentsClientHttpRequestFactory httpClientComponentsClientHttpRequestFactory() {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setHttpClient(buildHttpClient());
        return factory;
    }

    /**
     * 构建 HttpClient 实例
     */
    private CloseableHttpClient buildHttpClient() {
        try {
            // 连接池配置 - 使用 Apache HttpClient 5 API
            PoolingHttpClientConnectionManagerBuilder connectionManagerBuilder =
                    PoolingHttpClientConnectionManagerBuilder.create()
                            .setMaxConnTotal(restTemplateProperties.getConnectionPool().getMaxTotal())
                            .setMaxConnPerRoute(restTemplateProperties.getConnectionPool().getDefaultMaxPerRoute())
                            .setDefaultSocketConfig(
                                    SocketConfig.custom()
                                            .setSoTimeout(Timeout.ofMilliseconds(restTemplateProperties.getReadTimeout()))
                                            .setTcpNoDelay(true)
                                            .build()
                            );

            // 构建 HttpClient - 添加连接超时配置
            HttpClientBuilder builder = HttpClients.custom()
                    .setConnectionManager(connectionManagerBuilder.build())
                    .setDefaultRequestConfig(
                            RequestConfig.custom()
                                    .setConnectionRequestTimeout(Timeout.ofMilliseconds(restTemplateProperties.getConnectTimeout()))
                                    .build()
                    );

            return builder.build();
        } catch (Exception e) {
            log.error("Error building HttpClient", e);
            // 如果构建失败，返回默认的简单客户端
            return HttpClients.createDefault();
        }
    }

    /**
     * 配置 HTTP 消息转换器
     */
    private void configMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.removeIf(c -> c instanceof StringHttpMessageConverter || c instanceof MappingJackson2HttpMessageConverter);
        converters.add(new StringHttpMessageConverter(StandardCharsets.UTF_8));
        converters.add(new MappingJackson2HttpMessageConverter(objectMapper));
    }
}
