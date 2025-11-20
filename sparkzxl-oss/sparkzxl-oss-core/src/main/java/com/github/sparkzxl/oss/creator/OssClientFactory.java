package com.github.sparkzxl.oss.creator;

import com.github.sparkzxl.oss.client.OssClient;
import com.github.sparkzxl.oss.properties.Configuration;
import com.google.common.collect.Maps;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;

import java.util.List;
import java.util.Map;

/**
 * description: OssClientFactory
 *
 * @author zhouxinlei
 * @since 2025-11-20 08:34:23
 */
public class OssClientFactory {

    // 缓存：clientType -> 对应的创建器（初始化时加载）
    private final Map<String, OssClientCreator> CREATOR_MAP = Maps.newConcurrentMap();

    public OssClientFactory(List<OssClientCreator> ossClientCreators) {
        if (CollectionUtils.isNotEmpty(ossClientCreators)) {
            for (OssClientCreator ossClientCreator : ossClientCreators) {
                registerCreator(ossClientCreator);
            }
        }
    }

    /**
     * 手动注册创建器（新增OSS类型时，仅需调用该方法注册）
     */
    public void registerCreator(OssClientCreator creator) {
        if (creator == null || ObjectUtils.isEmpty(creator.supportClientType())) {
            throw new IllegalArgumentException("OSS创建器注册失败：创建器或clientType为空");
        }
        CREATOR_MAP.put(creator.supportClientType(), creator);
    }

    /**
     * 核心方法：根据配置创建OSS客户端（每次返回新实例）
     */
    public OssClient<?> buildOssClient(Configuration configuration) {
        // 1. 校验配置
        if (configuration == null) {
            throw new IllegalArgumentException("OSS配置不能为空");
        }
        String clientType = configuration.getClientType();
        if (ObjectUtils.isEmpty(clientType)) {
            throw new IllegalArgumentException("OSS客户端类型（clientType）未配置");
        }

        // 2. 找到对应的创建器
        OssClientCreator creator = CREATOR_MAP.get(clientType);
        if (creator == null) {
            throw new UnsupportedOperationException(
                    "不支持的OSS客户端类型：" + clientType + "，已支持类型：" + CREATOR_MAP.keySet()
            );
        }
        // 3. 创建新实例
        return creator.createClient(configuration);
    }
}
