package com.github.sparkzxl.oss.client;

import com.github.sparkzxl.oss.properties.Configuration;
import io.minio.MinioAsyncClient;
import io.minio.http.HttpUtils;
import okhttp3.OkHttpClient;

import java.util.concurrent.TimeUnit;

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
        OkHttpClient httpClient = HttpUtils.newDefaultHttpClient(
                TimeUnit.MINUTES.toMillis(5), TimeUnit.MINUTES.toMillis(15), TimeUnit.MINUTES.toMillis(5));
        MinioAsyncClient minioAsyncClient = MinioAsyncClient.builder().endpoint(configuration.getEndpoint())
                .credentials(configuration.getAccessKey(), configuration.getSecretKey())
                .httpClient(httpClient)
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
