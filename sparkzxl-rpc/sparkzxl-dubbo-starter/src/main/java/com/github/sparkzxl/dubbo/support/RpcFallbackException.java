package com.github.sparkzxl.dubbo.support;


import lombok.Getter;

/**
 * description: RPC降级异常
 *
 * @author zhouxinlei
 * @since 2025-09-05 13:47:55
 */
@Getter
public class RpcFallbackException extends RuntimeException {

    private static final long serialVersionUID = -7879378284877993323L;
    private final String errorCode;
    private final String message;

    public RpcFallbackException(String errorCode,
                                String message) {
        super(message);
        this.errorCode = errorCode;
        this.message = message;
    }

}
