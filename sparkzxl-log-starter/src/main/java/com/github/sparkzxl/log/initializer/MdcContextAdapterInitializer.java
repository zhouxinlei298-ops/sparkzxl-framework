package com.github.sparkzxl.log.initializer;

import ch.qos.logback.classic.LoggerContext;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.slf4j.TransmittableThreadLocalMdcAdapter;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.lang.NonNull;

/**
 * description: 初始化TtlMDCAdapter实例，替换SLF4J MDC门面和logback LoggerContext内部适配器
 *
 * @author zhouxinlei
 */
public class MdcContextAdapterInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(@NonNull ConfigurableApplicationContext applicationContext) {
        TransmittableThreadLocalMdcAdapter ttlAdapter = (TransmittableThreadLocalMdcAdapter) TransmittableThreadLocalMdcAdapter.getInstance();

        // 同步替换 logback LoggerContext 内部的 MDC 适配器
        // 否则 logback 创建 LoggingEvent 时会从原始 LogbackMDCAdapter 读 MDC，导致 event.getMDCPropertyMap() 为空
        ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();
        if (loggerFactory instanceof LoggerContext loggerContext) {
            loggerContext.setMDCAdapter(ttlAdapter);
        }
    }
}
