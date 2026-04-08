package com.github.sparkzxl.dubbo.fallback;

import lombok.Getter;

/**
 * description: 降级处理器持有者（包含处理器和客户端名称）
 *
 * @author zhouxinlei
 * @version 1.0
 * @since 2026-03-20 16:36:44
 */
@Getter
public class FallbackHandlerHolder {
    private final DubboFallbackHandler handler;
    private final String clientName;

    FallbackHandlerHolder(DubboFallbackHandler handler, String clientName) {
        this.handler = handler;
        this.clientName = clientName;
    }

}
