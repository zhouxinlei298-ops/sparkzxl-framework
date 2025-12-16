package com.github.sparkzxl.oss.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * description: Oss上传响应信息
 *
 * @author zhouxinlei
 * @since 2024-11-20 11:38:01
 */
@Data
public class OssPushObjectResponse implements Serializable {

    private static final long serialVersionUID = 8951568919270152871L;
    /**
     * bucket名称
     */
    private String bucketName;

    /**
     * 文件名称
     */
    private String objectName;

    /**
     * 文件大小
     */
    private long size;

    /**
     * 文件数据类型
     */
    private String contentType;

    /**
     * 文件上传时间
     */
    private LocalDateTime uploadTime;

    /**
     * 文件地址
     */
    private String url;
}
