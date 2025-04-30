package com.github.sparkzxl.oss.enums;

import lombok.Getter;

/**
 * description: 上传进度枚举类
 *
 * @author zhouxinlei
 * @since 2025-04-30 09:15:13
 */
@Getter
public enum UploadCodeEnum {

    UPLOAD_SUCCESS(2001, "上传成功"),
    UPLOADING(2002, "上传中"),
    NOT_UPLOADED(2003, "未上传"),
    ;


    private final int code;
    private final String msg;

    UploadCodeEnum(int code, String errorMessage) {
        this.code = code;
        this.msg = errorMessage;
    }

}
