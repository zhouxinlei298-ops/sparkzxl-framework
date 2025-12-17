package com.github.sparkzxl.oss.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * description: OSS文件信息
 *
 * @author zhouxinlei
 * @since 2025-12-15 16:00:28
 */
@Data
public class OssMetadata implements Serializable {

    private static final long serialVersionUID = -2556687951230056610L;
    private String bucketName;
    private String objectName;
    private String etag;
    private long size;
    private String contentType;
    private LocalDateTime lastModified;
    private Map<String, String> userMetadata;
}
