package com.github.sparkzxl.oss.client;

import com.github.sparkzxl.oss.properties.Configuration;
import lombok.extern.slf4j.Slf4j;

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

    public RustfsOssClient(Configuration configuration) {
        this.configuration = configuration;
        this.client = new CustomRustfsClient(configuration);
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
    }
}
