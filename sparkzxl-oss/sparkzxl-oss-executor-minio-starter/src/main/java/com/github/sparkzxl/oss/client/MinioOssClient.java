package com.github.sparkzxl.oss.client;

import com.github.sparkzxl.oss.properties.Configuration;
import io.minio.MinioAsyncClient;

/**
 * description: MinioOssClient
 *
 * @author zhouxinlei
 * @since 2022-10-12 09:14:42
 */
public class MinioOssClient implements OssClient<CustomMinioClient> {

    private final CustomMinioClient client;
    private final Configuration configuration;

    public MinioOssClient(Configuration configuration) {
        this.configuration = configuration;
        MinioAsyncClient minioAsyncClient = MinioAsyncClient.builder().endpoint(configuration.getEndpoint())
                .credentials(configuration.getAccessKey(), configuration.getSecretKey())
                .build();
        client = new CustomMinioClient(minioAsyncClient);
    }

    @Override
    public CustomMinioClient getClient() {
        return this.client;
    }

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }
}
