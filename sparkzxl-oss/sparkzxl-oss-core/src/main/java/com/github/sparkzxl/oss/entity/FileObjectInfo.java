package com.github.sparkzxl.oss.entity;

import lombok.Data;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;

/**
 * description: 文件对象信息
 *
 * @author zhouxinlei
 * @since 2025-04-28 16:19:44
 */
@Data
@Accessors(chain = true)
public class FileObjectInfo implements Serializable {

    private static final long serialVersionUID = -7584640449792745942L;
    /**
     * 文件名称
     */
    private String fileName;
    /**
     * OSS对象名称
     */
    private String objectName;

    /**
     * 文件后缀名
     */
    private String suffix;

}
