package com.github.sparkzxl.alarm.sign;

import lombok.Getter;
import lombok.Setter;

/**
 * description: 签名返回体
 *
 * @author zhouxinlei
 * @since 2022-05-18 15:54:18
 */
@Setter
@Getter
public class SignResult extends BaseSign {

    /**
     * 秘钥
     */
    private String sign;
    /**
     * 时间戳
     */
    private Long timestamp;

    public SignResult(String sign, Long timestamp) {
        this.sign = sign;
        this.timestamp = timestamp;
    }

    @Override
    public String transfer() {
        return SEPERATOR + "sign=" + this.sign +
                SEPERATOR + "timestamp=" + this.timestamp;
    }
}
