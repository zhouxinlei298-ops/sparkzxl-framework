package com.github.sparkzxl.oss.client;

import com.github.sparkzxl.oss.properties.Configuration;

/**
 * description: Oss Client
 *
 * @author zhouxinlei
 * @since 2022-10-12 16:21:43
 */
public interface OssClient<T> extends AutoCloseable {

    /**
     * 获取client
     *
     * @return T
     */
    T getClient();

    /**
     * 获取配置
     *
     * @return Configuration
     */
    Configuration getConfiguration();

    /**
     * 关闭客户端，释放资源
     * <p>
     * 实现类应在此方法中释放所有持有的资源，如网络连接、线程池等
     * </p>
     */
    @Override
    void close();

}
