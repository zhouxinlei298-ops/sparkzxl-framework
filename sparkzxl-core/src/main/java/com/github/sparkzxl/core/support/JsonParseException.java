package com.github.sparkzxl.core.support;

import com.github.sparkzxl.core.support.code.ExceptionErrorCode;
import lombok.Getter;

/**
 * description: JSON解析异常类
 *
 * @author zhouxinlei
 */
@Getter
public class JsonParseException extends BaseUncheckedException {

    private static final long serialVersionUID = 6898087804057803400L;

    public JsonParseException() {
        super(ExceptionErrorCode.JSON_TRANSFORM_ERROR);
    }

    public JsonParseException(String errorMsg) {
        super(ExceptionErrorCode.JSON_TRANSFORM_ERROR.getErrorCode(), errorMsg);
    }
}
