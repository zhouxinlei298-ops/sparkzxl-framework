package com.github.sparkzxl.boot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * description:
 *
 * @author zhouxinlei
 * @since 2025-08-08 13:53:33
 */
@Configuration(proxyBeanMethods = false)
@Import(WebServerConfiguration.class)
public class UndertowAutoConfiguration {
}
