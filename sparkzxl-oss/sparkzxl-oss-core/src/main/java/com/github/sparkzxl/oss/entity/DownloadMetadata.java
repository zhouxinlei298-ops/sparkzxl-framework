package com.github.sparkzxl.oss.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文件下载元信息，用于downloadMultipartFile模板方法传递元数据
 *
 * @author zhouxinlei
 * @since 2026-05-14
 */
@Getter
@AllArgsConstructor
public class DownloadMetadata {

    private final long size;
    private final String contentType;
    private final String lastModified;
    private final String eTag;
}
