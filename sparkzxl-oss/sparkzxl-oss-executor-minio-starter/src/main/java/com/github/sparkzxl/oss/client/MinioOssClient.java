package com.github.sparkzxl.oss.client;

import com.github.sparkzxl.oss.properties.Configuration;
import io.minio.MinioAsyncClient;
import io.minio.http.HttpUtils;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;

import java.util.concurrent.TimeUnit;

/**
 * description: MinioOssClient
 *
 * @author zhouxinlei
 * @since 2022-10-12 09:14:42
 */
@Slf4j
public class MinioOssClient implements OssClient<CustomMinioClient> {

    private final CustomMinioClient client;
    private final Configuration configuration;
    private final Dispatcher dispatcher;
    private final OkHttpClient httpClient;

    public MinioOssClient(Configuration configuration) {
        this.configuration = configuration;
        // 创建自定义 Dispatcher，增加并发容量避免 executor rejected 错误
        this.dispatcher = new Dispatcher();
        this.dispatcher.setMaxRequests(200);
        this.dispatcher.setMaxRequestsPerHost(50);
        this.httpClient = HttpUtils.newDefaultHttpClient(
                        TimeUnit.MINUTES.toMillis(5),
                        TimeUnit.MINUTES.toMillis(15),
                        TimeUnit.MINUTES.toMillis(5))
                .newBuilder()
                .dispatcher(dispatcher)
                .build();
        MinioAsyncClient minioAsyncClient = MinioAsyncClient.builder()
                .endpoint(configuration.getEndpoint())
                .credentials(configuration.getAccessKey(), configuration.getSecretKey())
                .httpClient(httpClient)
                .build();
        this.client = new CustomMinioClient(minioAsyncClient);
    }

    @Override
    public CustomMinioClient getClient() {
        return this.client;
    }

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public void close() {
        // 先关闭 dispatcher，停止接受新任务并等待现有任务完成
        if (dispatcher != null) {
            try {
                dispatcher.executorService().shutdown();
                dispatcher.cancelAll();
                log.debug("Minio dispatcher closed successfully");
            } catch (Exception e) {
                log.error("Error closing Minio dispatcher: {}", e.getMessage(), e);
            }
        }
        // 关闭 OkHttpClient
        if (httpClient != null) {
            try {
                httpClient.dispatcher().executorService().shutdown();
                httpClient.connectionPool().evictAll();
                log.debug("Minio HTTP client closed successfully");
            } catch (Exception e) {
                log.error("Error closing Minio HTTP client: {}", e.getMessage(), e);
            }
        }
        // 关闭 MinioAsyncClient
        if (client != null) {
            try {
                client.close();
                log.debug("Minio client closed successfully");
            } catch (Exception e) {
                log.error("Error closing Minio client: {}", e.getMessage(), e);
            }
        }
    }
}
