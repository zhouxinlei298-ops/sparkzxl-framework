package com.github.sparkzxl.oss.entity;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

/**
 * description: 返回文件生成的分片上传地址
 *
 * @author zhouxinlei
 * @since 2025-03-08 20:23:29
 */
@Data
@Accessors(chain = true)
public class UploadUrlsInfo implements Serializable {

    private static final long serialVersionUID = 8081460803650206427L;
    private String uploadId;
    private List<String> urls;
}
