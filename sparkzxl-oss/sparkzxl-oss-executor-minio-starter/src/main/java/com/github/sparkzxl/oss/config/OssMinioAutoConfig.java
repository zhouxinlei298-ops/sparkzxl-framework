package com.github.sparkzxl.oss.config;

import com.github.sparkzxl.oss.creator.MinioOssClientCreator;
import com.github.sparkzxl.oss.creator.OssClientCreator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * description: minio 自动装配
 *
 * @author zhouxinlei
 * @since 2025-11-20 09:26:13
 */
@Configuration
public class OssMinioAutoConfig {

    @Bean
    public OssClientCreator minioOssClientCreator() {
        return new MinioOssClientCreator();
    }
}
