package com.github.sparkzxl.oss.properties;

import lombok.Data;

import java.io.Serializable;
import java.net.URI;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * oss属性配置信息
 *
 * @author zhouxinlei
 * @since 2026-10-07
 */
@Data
public class Configuration implements Serializable {

    private static final long serialVersionUID = 3576218154929292921L;

    private String region = "";

    private boolean readEnabled = true;

    private boolean writeEnabled = true;

    private long revision = 1;

    private long locationRevision = 1;

    private String objectPrefix = "";

    /** Client Id */
    @NotBlank(message = "OSS clientId cannot be blank")
    @Size(max = 128, message = "OSS clientId length cannot exceed 128 characters")
    private String clientId;

    /** Oss Client Type (aliyun, minio, rustfs) */
    @NotBlank(message = "OSS clientType cannot be blank")
    @Pattern(
            regexp = "^[a-z][a-z0-9_-]{0,15}$",
            message = "OSS clientType must be a lowercase identifier of at most 16 characters")
    private String clientType;

    /** 对象存储服务的URL */
    @NotBlank(message = "OSS endpoint cannot be blank")
    @Size(max = 512, message = "OSS endpoint length cannot exceed 512 characters")
    private String endpoint;

    /** 自定义域名 */
    @Size(max = 512, message = "OSS domain length cannot exceed 512 characters")
    private String domain;

    /** Access key就像用户ID，可以唯一标识你的账户 */
    @NotBlank(message = "OSS accessKey cannot be blank")
    @Size(min = 1, max = 256, message = "OSS accessKey length must be between 1 and 256 characters")
    private String accessKey;

    /** Secret key是你账户的密码 */
    @NotBlank(message = "OSS secretKey cannot be blank")
    @Size(min = 1, max = 256, message = "OSS secretKey length must be between 1 and 256 characters")
    private String secretKey;

    /** 默认的存储桶名称 */
    @Size(max = 64, message = "OSS bucketName length cannot exceed 63 characters")
    private String bucketName = "sparkzxl";

    /** 上传文件限制白名单，逗号分割（如：jpg,png,pdf） */
    @Size(max = 512, message = "OSS fileFormat length cannot exceed 512 characters")
    private String fileFormat;

    private static void validateHttpAddress(String value, String field) {
        try {
            URI uri = value == null ? null : URI.create(value);
            if (uri == null
                    || uri.getHost() == null
                    || !("http".equalsIgnoreCase(uri.getScheme())
                            || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getUserInfo() != null
                    || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("OSS " + field + "必须为有效的HTTP或HTTPS地址");
        }
    }

    /**
     * 重写 toString 方法，防止敏感信息泄露到日志
     *
     * @return 脱敏后的配置字符串
     */
    @Override
    public String toString() {
        return "Configuration[clientId=" + clientId + ", revision=" + revision + "]";
    }

    /**
     * 创建供应商客户端前校验公共必填参数；可选参数不强制填写。
     *
     * @throws IllegalArgumentException 配置缺失或地址非法，消息不包含凭据
     */
    public void validateClientConfiguration() {
        validateHttpAddress(endpoint, "Endpoint");
        if (accessKey == null
                || accessKey.trim().isEmpty()
                || secretKey == null
                || secretKey.trim().isEmpty()) {
            throw new IllegalArgumentException("OSS credentials are unavailable");
        }
        if (bucketName == null || bucketName.trim().isEmpty()) {
            throw new IllegalArgumentException("OSS Bucket不能为空");
        }
        if (domain != null && !domain.isEmpty()) {
            validateHttpAddress(domain, "访问域名");
        }
    }

    /**
     * 独立配置副本，禁止将其作为业务输出或日志载荷。
     *
     * @return 与原配置隔离的防御性副本
     */
    public Configuration copy() {
        Configuration c = new Configuration();
        c.setRegion(getRegion());
        c.setReadEnabled(isReadEnabled());
        c.setWriteEnabled(isWriteEnabled());
        c.setClientId(getClientId());
        c.setClientType(getClientType());
        c.setEndpoint(getEndpoint());
        c.setDomain(getDomain());
        c.setAccessKey(getAccessKey());
        c.setSecretKey(getSecretKey());
        c.setBucketName(getBucketName());
        c.setFileFormat(getFileFormat());
        c.setRevision(getRevision());
        c.setLocationRevision(getLocationRevision());
        c.setObjectPrefix(getObjectPrefix());
        return c;
    }
}
