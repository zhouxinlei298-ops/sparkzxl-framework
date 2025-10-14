package com.github.sparkzxl.boot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * description:
 *
 * @author zhouxinlei
 * @since 2025-09-01 09:35:01
 */
@Configuration(proxyBeanMethods = false)
@Import(WebServerConfiguration.class)
public class UndertowAutoConfiguration {
}
