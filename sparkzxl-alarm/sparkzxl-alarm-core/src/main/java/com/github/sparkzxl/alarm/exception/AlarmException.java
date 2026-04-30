package com.github.sparkzxl.alarm.exception;

import com.github.sparkzxl.alarm.enums.AlarmErrorEnum;
import com.github.sparkzxl.core.support.BaseUncheckedException;
import com.github.sparkzxl.core.support.code.IErrorCode;
import lombok.Getter;
import lombok.Setter;

/**
 * 异常类
 *
 * @author zhouxinlei
 * @since 1.0
 */
@Setter
@Getter
public class AlarmException extends BaseUncheckedException {

    public AlarmException(String errorCode, String errorMsg) {
        super(errorCode, errorMsg);
    }

    public AlarmException(IErrorCode errorCode) {
        super(errorCode);
    }

    public AlarmException(Throwable cause) {
        super(AlarmErrorEnum.FAILED.getErrorCode(), cause.getMessage(), cause);
    }

    public AlarmException(Throwable cause, String errorCode, String errorMsg) {
        super(errorCode, errorMsg, cause);
    }
}
