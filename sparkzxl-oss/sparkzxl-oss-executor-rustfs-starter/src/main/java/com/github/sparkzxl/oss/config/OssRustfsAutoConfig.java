package com.github.sparkzxl.oss.config;

import com.github.sparkzxl.oss.creator.RustfsOssClientCreator;
import com.github.sparkzxl.oss.creator.OssClientCreator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * description: Rustfs 自动装配
 *
 * @author zhouxinlei
 * @since 2025-11-20 09:26:13
 */
@Configuration
public class OssRustfsAutoConfig {

    @Bean
    public OssClientCreator rustfsOssClientCreator() {
        return new RustfsOssClientCreator();
    }
}
