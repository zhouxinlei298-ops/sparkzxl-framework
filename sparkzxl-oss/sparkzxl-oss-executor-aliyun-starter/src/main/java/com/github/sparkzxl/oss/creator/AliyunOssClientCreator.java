package com.github.sparkzxl.oss.creator;


import com.github.sparkzxl.oss.client.AliyunOssClient;
import com.github.sparkzxl.oss.client.OssClient;
import com.github.sparkzxl.oss.properties.Configuration;

/**
 * description: aliyun oss 客户端创建器
 *
 * @author zhouxinlei
 * @since 2025-11-20 08:31:23
 */
public class AliyunOssClientCreator implements OssClientCreator {

    @Override
    public String supportClientType() {
        return "aliyun";
    }

    @Override
    public OssClient<?> createClient(Configuration configuration) {
        return new AliyunOssClient(configuration);
    }
}
