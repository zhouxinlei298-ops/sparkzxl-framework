package com.github.sparkzxl.oss.client;

import com.github.sparkzxl.oss.properties.Configuration;
import com.github.sparkzxl.oss.support.OssErrorCode;
import com.github.sparkzxl.oss.support.OssException;
import com.github.sparkzxl.spi.ExtensionLoader;
import org.apache.commons.lang3.ObjectUtils;

import java.text.MessageFormat;

/**
 * description: OssClient 工厂
 *
 * @author zhouxinlei
 * @since 2025-11-19 14:38:40
 */
public class OssClientFactory {

    /**
     * New instance IOssClient.
     *
     * @param clientType the oss client type
     * @return IOssClient
     */
    public static OssClient<?> newInstance(final String clientType) {
        return ExtensionLoader.getExtensionLoader(OssClient.class).getJoin(clientType);
    }

    public static OssClient<?> buildOssClient(Configuration configuration) {
        String clientType = configuration.getClientType();
        OssClient<?> ossClient = newInstance(clientType);
        if (ObjectUtils.isEmpty(ossClient)) {
            String errorMsg = MessageFormat.format(OssErrorCode.OSS_TYPE_UNSUPPORTED.getErrorMsg(),
                    clientType, clientType);
            throw new OssException(OssErrorCode.OSS_TYPE_UNSUPPORTED.getErrorCode(), errorMsg);
        }
        return ossClient.init(configuration);
    }

}
