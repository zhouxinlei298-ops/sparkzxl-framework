package com.github.sparkzxl.oss.config;

import com.github.sparkzxl.oss.creator.AliyunOssClientCreator;
import com.github.sparkzxl.oss.creator.OssClientCreator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * description: aliyun oss 自动装配
 *
 * @author zhouxinlei
 * @since 2025-11-20 09:26:13
 */
@Configuration
public class OssAliyunAutoConfig {

    @Bean
    public OssClientCreator aliyunOssClientCreator() {
        return new AliyunOssClientCreator();
    }
}
