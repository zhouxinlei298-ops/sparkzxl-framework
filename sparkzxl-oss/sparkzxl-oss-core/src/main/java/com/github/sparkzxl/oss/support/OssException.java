package com.github.sparkzxl.oss.support;

import com.github.sparkzxl.core.support.BaseUncheckedException;
import com.github.sparkzxl.core.support.code.IErrorCode;

/**
 * description: oss异常
 *
 * @author zhouxinlei
 * @since 2022-05-03 17:13:39
 */
public class OssException extends BaseUncheckedException {

    public OssException(String errorCode, String errorMsg) {
        super(errorCode, errorMsg);
    }

    public OssException(IErrorCode errorCode) {
        super(errorCode);
    }

    public OssException(IErrorCode errorCode, Throwable cause) {
        super(errorCode.getErrorCode(), errorCode.getErrorMsg(), cause);
    }

    public OssException(String errorCode, String errorMsg, Throwable cause) {
        super(errorCode, errorMsg, cause);
    }
}
