package com.github.sparkzxl.core.entity;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * description: 上传文件存储模型
 *
 * @author zhouxinlei
 * @since 2025-12-16 09:01:26
 */
@Data
@Accessors(chain = true)
public class UploadFileModel implements Serializable {

    private static final long serialVersionUID = 2336465300990637550L;
    /**
     * 文件id
     */
    private Long fileId;

    /**
     * 文件名称
     */
    private String originalFileName;

    /**
     * 文件地址
     */
    private String fileUrl;

    /**
     * 文件相对路径
     */
    private String objectName;

    /**
     * 标签：是否被使用过
     */
    private String tag;

    /**
     * 文件大小
     */
    private Long size;

    /**
     * 文件MD5
     */
    private String md5;
}
