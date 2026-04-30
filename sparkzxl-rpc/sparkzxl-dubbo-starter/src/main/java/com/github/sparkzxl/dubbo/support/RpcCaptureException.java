package com.github.sparkzxl.dubbo.support;


import com.github.sparkzxl.core.support.BaseUncheckedException;
import lombok.Getter;

/**
 * description: RPC捕获异常
 *
 * @author zhouxinlei
 * @since 2025-09-05 13:47:55
 */
@Getter
public class RpcCaptureException extends BaseUncheckedException {

    private static final long serialVersionUID = 1240286214306806695L;
    private final String serviceName;

    public RpcCaptureException(String serviceName,
                               String errorCode,
                               String errorMessage) {
        super(errorCode, errorMessage);
        this.serviceName = serviceName;
    }

}
