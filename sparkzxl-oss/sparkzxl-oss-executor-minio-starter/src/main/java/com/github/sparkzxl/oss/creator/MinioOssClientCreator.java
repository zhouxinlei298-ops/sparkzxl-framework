package com.github.sparkzxl.oss.creator;


import com.github.sparkzxl.oss.client.MinioOssClient;
import com.github.sparkzxl.oss.client.OssClient;
import com.github.sparkzxl.oss.properties.Configuration;

/**
 * description: MINIO OSS 客户端创建器
 *
 * @author zhouxinlei
 * @since 2025-11-20 08:29:46
 */
public class MinioOssClientCreator implements OssClientCreator{

    @Override
    public String supportClientType() {
        return "minio";
    }

    @Override
    public OssClient<?> createClient(Configuration configuration) {
        return new MinioOssClient(configuration);
    }
}
