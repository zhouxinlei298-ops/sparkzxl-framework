package com.github.sparkzxl.oss.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;

/**
 * description: 文件上传信息，查询 redis 后的返回信息
 *
 * @author zhouxinlei
 * @since 2025-03-08 20:16:59
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class FileUploadInfo extends FileObjectInfo implements Serializable {

    private static final long serialVersionUID = -7535060694696918090L;
    @NotBlank(message = "md5 不能为空")
    private String md5;

    @NotBlank(message = "uploadId 不能为空")
    private String uploadId;

    @NotBlank(message = "文件名不能为空")
    private String originFileName;

    @NotNull(message = "文件大小不能为空")
    private Long size;

    @NotNull(message = "分片数量不能为空")
    private Integer chunkCount;

    @NotNull(message = "分片大小不能为空")
    private Long chunkSize;

    /**
     * 仅秒传会有值
     */
    private String url;

    /**
     * 文件类型
     */
    private String contentType;

    /**
     * listParts 从 1 开始，前端需要上传的分片索引+1
     */
    private List<PartData> listParts;

    /**
     * 上传进度 2001:上传成功,2002:上传中,2003:未上传
     */
    private Integer progress;

}
