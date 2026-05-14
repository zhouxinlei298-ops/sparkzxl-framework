package com.github.sparkzxl.log.initializer;

import org.slf4j.TransmittableThreadLocalMdcAdapter;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.lang.NonNull;

/**
 * description: 初始化TtlMDCAdapter实例，替换SLF4J MDC门面中的adapter对象
 * <p>
 * logback 1.2.x 的 LoggingEvent 通过 MDC.getCopyOfContextMap() 读取 MDC，
 * 因此只需替换 SLF4J 的 MDC.mdcAdapter 即可，无需额外同步 LoggerContext。
 *
 * @author zhouxinlei
 */
public class MdcContextAdapterInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(@NonNull ConfigurableApplicationContext applicationContext) {
        TransmittableThreadLocalMdcAdapter.getInstance();
    }
}
