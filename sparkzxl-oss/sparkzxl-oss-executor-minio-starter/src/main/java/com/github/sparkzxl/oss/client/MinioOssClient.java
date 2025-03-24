package com.github.sparkzxl.oss.client;

import com.github.sparkzxl.oss.properties.Configuration;
import com.github.sparkzxl.spi.Join;
import io.minio.MinioAsyncClient;
import lombok.Setter;

/**
 * description: MinioOssClient
 *
 * @author zhouxinlei
 * @since 2022-10-12 09:14:42
 */
@Setter
@Join
public class MinioOssClient implements OssClient<CustomMinioClient> {

    private CustomMinioClient client;
    private Configuration configuration;

    public MinioOssClient() {
    }

    @Override
    public OssClient<CustomMinioClient> init(Configuration configuration) {
        this.configuration = configuration;
        MinioAsyncClient minioAsyncClient = MinioAsyncClient.builder().endpoint(configuration.getEndpoint())
                .credentials(configuration.getAccessKey(), configuration.getSecretKey())
                .build();
        client = new CustomMinioClient(minioAsyncClient);
        return this;
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
