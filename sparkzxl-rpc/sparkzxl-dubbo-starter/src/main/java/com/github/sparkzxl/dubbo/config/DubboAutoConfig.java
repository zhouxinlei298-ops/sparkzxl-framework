package com.github.sparkzxl.dubbo.config;

import com.github.sparkzxl.dubbo.properties.DubboExtensionProperties;
import com.github.sparkzxl.dubbo.support.DubboExceptionHandler;
import com.github.sparkzxl.dubbo.support.ExceptionHandlerMethodProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * description: dubbo定制化自动装配类
 *
 * @author zhouxinlei
 */
@Configuration
@EnableConfigurationProperties(DubboExtensionProperties.class)
@Import({DubboExceptionHandler.class, ExceptionHandlerMethodProcessor.class})
public class DubboAutoConfig {

}
