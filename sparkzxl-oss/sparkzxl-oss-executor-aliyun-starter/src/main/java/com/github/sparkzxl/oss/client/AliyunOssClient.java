package com.github.sparkzxl.oss.client;

import com.aliyun.oss.OSSClient;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.github.sparkzxl.oss.properties.Configuration;

/**
 * description: AliyunOssClient
 *
 * @author zhouxinlei
 * @since 2022-10-12 09:14:42
 */
public class AliyunOssClient implements OssClient<OSSClient> {

    private final OSSClient client;
    private final Configuration configuration;

    public AliyunOssClient(Configuration configuration) {
        this.configuration = configuration;
        DefaultCredentialProvider defaultCredentialProvider = new DefaultCredentialProvider(
                configuration.getAccessKey(), configuration.getSecretKey());
        this.client = new OSSClient(configuration.getEndpoint(), defaultCredentialProvider, null);
    }

    @Override
    public OSSClient getClient() {
        return this.client;
    }

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }

}
