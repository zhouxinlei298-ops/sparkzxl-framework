package com.github.sparkzxl.oss.operation;

import com.github.sparkzxl.oss.properties.Configuration;

/**
 * 校验显式客户端标识并构造不可变操作目标。
 *
 * @author zhouxinlei
 * @since 2026-10-06
 * @see OssOperationContextManager
 */
public final class OssOperationContextResolver {

    private OssOperationContextResolver() {}

    /**
     * 解析并校验目标配置或本地服务账号凭据。
     *
     * @param clientId 固定客户端标识
     * @param c 待校验配置快照
     * @return 校验通过的不可变操作目标
     */
    public static OssOperationContext resolve(String clientId, Configuration c) {
        if (clientId == null
                || clientId.trim().isEmpty()
                || c == null
                || !clientId.equals(c.getClientId())) {
            throw new IllegalStateException("Explicit OSS configuration is missing or mismatched");
        }
        if (c.getRevision() <= 0
                || c.getLocationRevision() <= 0
                || blank(c.getClientType())
                || blank(c.getEndpoint())
                || blank(c.getBucketName())
                || blank(c.getAccessKey())
                || blank(c.getSecretKey())
                || c.getObjectPrefix() == null) {
            throw new IllegalArgumentException("Invalid OSS configuration snapshot");
        }
        String prefix = c.getObjectPrefix();
        if (!prefix.isEmpty()
                && (!prefix.endsWith("/") || prefix.startsWith("/") || prefix.indexOf('\\') >= 0)) {
            throw new IllegalArgumentException("Invalid OSS object prefix");
        }
        for (String segment : prefix.split("/")) {
            if (segment.equals(".") || segment.equals("..") || segment.isEmpty()) {
                if (!prefix.isEmpty()) {
                    throw new IllegalArgumentException("Invalid OSS object prefix");
                }
            }
        }
        return new OssOperationContext(c);
    }

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
