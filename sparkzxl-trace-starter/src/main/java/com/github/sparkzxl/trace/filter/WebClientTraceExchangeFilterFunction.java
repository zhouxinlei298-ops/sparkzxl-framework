package com.github.sparkzxl.trace.filter;

import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * WebClient ExchangeFilterFunction，从 Reactor Context 读取 propagateHeaders
 * 写入出站请求的 HTTP headers，实现 trace 上下文在 WebClient 调用链中的自动传播。
 *
 * @author zhouxinlei
 */
public class WebClientTraceExchangeFilterFunction implements ExchangeFilterFunction {

    private final List<String> propagateHeaders;

    public WebClientTraceExchangeFilterFunction(List<String> propagateHeaders) {
        this.propagateHeaders = propagateHeaders;
    }

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        return Mono.deferContextual(contextView -> {
            ClientRequest.Builder builder = ClientRequest.from(request);
            for (String header : propagateHeaders) {
                String value = contextView.getOrDefault(header, "");
                if (!value.isEmpty()) {
                    builder.header(header, value);
                }
            }
            return next.exchange(builder.build());
        });
    }
}
