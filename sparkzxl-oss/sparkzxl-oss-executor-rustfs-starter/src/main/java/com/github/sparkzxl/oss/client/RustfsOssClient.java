package com.github.sparkzxl.oss.client;

import com.github.sparkzxl.oss.properties.Configuration;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.time.Duration;

/**
 * description: RustfsOssClient
 *
 * @author zhouxinlei
 * @since 2022-10-12 09:14:42
 */
@Slf4j
public class RustfsOssClient implements OssClient<CustomRustfsClient> {

    private final CustomRustfsClient client;
    private final Configuration configuration;
    private final SdkHttpClient apache5HttpClient;

    public RustfsOssClient(Configuration configuration) {
        this.configuration = configuration;
        this.apache5HttpClient = Apache5HttpClient.builder()
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(15))
                .build();
        this.client = new CustomRustfsClient(S3Client.builder()
                // RustFS 地址
                .endpointOverride(URI.create(configuration.getEndpoint()))
                // 可写死，RustFS 不校验 region
                .region(Region.US_EAST_1)
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(configuration.getAccessKey(),
                                        configuration.getSecretKey())
                        )
                )
                // 关键配置！RustFS 需启用 Path-Style
                .forcePathStyle(true)
                .httpClient(apache5HttpClient)
                .overrideConfiguration(
                        b -> b.apiCallTimeout(Duration.ofSeconds(5))
                                .apiCallAttemptTimeout(Duration.ofMillis(2000)))
                .build());
    }

    @Override
    public CustomRustfsClient getClient() {
        return this.client;
    }

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public void close() {
        if (client != null) {
            try {
                client.shutdown();
                log.debug("Rustfs client closed successfully");
            } catch (Exception e) {
                log.error("Error closing Rustfs client: {}", e.getMessage(), e);
            }
        }
        // 关闭 Apache5 HTTP 客户端
        if (apache5HttpClient != null) {
            try {
                apache5HttpClient.close();
                log.debug("Rustfs HTTP client closed successfully");
            } catch (Exception e) {
                log.error("Error closing Rustfs HTTP client: {}", e.getMessage(), e);
            }
        }
    }
}
