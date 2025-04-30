package com.github.sparkzxl.oss.entity;

import lombok.Data;

import java.io.Serializable;

/**
 * description: 分片数据
 *
 * @author zhouxinlei
 * @since 2025-04-30 14:15:21
 */
@Data
public class PartData implements Serializable {

    private static final long serialVersionUID = -6789979130298732872L;
    /**
     * 分片号
     */
    private Integer partNumber;

    /**
     * ETag
     */
    private String etag;

    /**
     * 分片大小
     */
    private Long size;
}
