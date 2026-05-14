package com.github.sparkzxl.log.layout;

import ch.qos.logback.classic.PatternLayout;

/**
 * local 模式下的 TraceId Layout，参照 SkyWalking 的 TraceIdMDCPatternLogbackLayout。
 * 注册 LocalMDCPatternConverter 拦截 %X{tid}，tid 不存在时输出 "N/A"。
 *
 * @author zhouxinlei
 * @since 2026-04-24
 */
public class LocalTraceIdMDCPatternLogbackLayout extends PatternLayout {

    static {
        DEFAULT_CONVERTER_MAP.put("X", LocalMDCPatternConverter.class.getName());
    }
}
