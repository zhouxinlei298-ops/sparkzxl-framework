package com.github.sparkzxl.oss.properties;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * description: oss属性配置信息
 *
 * @author zhouxinlei
 */
@Data
public class Configuration implements Serializable {

    private static final long serialVersionUID = 3576218154929292921L;
    /**
     * Client Id
     */
    @NotBlank(message = "OSS clientId cannot be blank")
    @Size(max = 128, message = "OSS clientId length cannot exceed 128 characters")
    private String clientId;

    /**
     * Oss Client Type (aliyun, minio, rustfs)
     */
    @NotBlank(message = "OSS clientType cannot be blank")
    @Pattern(regexp = "^(aliyun|minio|rustfs)$", message = "OSS clientType must be one of: aliyun, minio, rustfs")
    private String clientType;

    /**
     * 对象存储服务的URL
     */
    @NotBlank(message = "OSS endpoint cannot be blank")
    @Size(max = 512, message = "OSS endpoint length cannot exceed 512 characters")
    private String endpoint;

    /**
     * 自定义域名
     */
    @Size(max = 512, message = "OSS domain length cannot exceed 512 characters")
    private String domain;

    /**
     * Access key就像用户ID，可以唯一标识你的账户
     */
    @NotBlank(message = "OSS accessKey cannot be blank")
    @Size(min = 1, max = 256, message = "OSS accessKey length must be between 1 and 256 characters")
    private String accessKey;

    /**
     * Secret key是你账户的密码
     */
    @NotBlank(message = "OSS secretKey cannot be blank")
    @Size(min = 1, max = 256, message = "OSS secretKey length must be between 1 and 256 characters")
    private String secretKey;

    /**
     * 默认的存储桶名称
     */
    @Size(max = 64, message = "OSS bucketName length cannot exceed 63 characters")
    private String bucketName = "sparkzxl";

    /**
     * 上传文件限制白名单，逗号分割（如：jpg,png,pdf）
     */
    @Size(max = 512, message = "OSS fileFormat length cannot exceed 512 characters")
    private String fileFormat;

    /**
     * 重写 toString 方法，防止敏感信息泄露到日志
     *
     * @return 脱敏后的配置字符串
     */
    @Override
    public String toString() {
        return "Configuration{" +
                "clientId='" + clientId + '\'' +
                ", clientType='" + clientType + '\'' +
                ", endpoint='" + endpoint + '\'' +
                ", domain='" + domain + '\'' +
                ", accessKey='" + maskSensitive(accessKey, 4) + '\'' +
                ", secretKey='******'" +
                ", bucketName='" + bucketName + '\'' +
                ", fileFormat='" + fileFormat + '\'' +
                '}';
    }

    /**
     * 对敏感信息进行脱敏处理
     *
     * @param value 原始值
     * @param visibleChars 保留可见的字符数
     * @return 脱敏后的值
     */
    private String maskSensitive(String value, int visibleChars) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.length() <= visibleChars) {
            return value;
        }
        return value.substring(0, visibleChars) + "******";
    }
}
