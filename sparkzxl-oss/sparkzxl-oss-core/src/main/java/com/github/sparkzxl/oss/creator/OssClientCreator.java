package com.github.sparkzxl.oss.creator;

import com.github.sparkzxl.oss.OssClientType;
import com.github.sparkzxl.oss.client.OssClient;
import com.github.sparkzxl.oss.properties.Configuration;

/**
 * OSS客户端创建器接口：每个OSS类型实现该接口，负责创建自身实例
 *
 * @author zhouxinlei
 * @since 2025-11-19 17:12:36
 */
public interface OssClientCreator {

    /**
     * 支持的clientType
     *
     * @return 与配置和执行器SPI一致的稳定供应商标识
     */
    String supportClientType();

    /**
     * 返回类型值对象，保留现有字符串实现的兼容入口。
     *
     * @return 创建器支持的供应商类型
     */
    default OssClientType clientType() {
        return OssClientType.of(supportClientType());
    }

    /**
     * 创建新的客户端实例
     *
     * @param configuration 固定操作配置
     * @return 对应供应商的新客户端
     */
    OssClient<?> createClient(Configuration configuration);
}
