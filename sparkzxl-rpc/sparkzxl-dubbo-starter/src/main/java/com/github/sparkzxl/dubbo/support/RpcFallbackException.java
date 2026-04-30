package com.github.sparkzxl.dubbo.support;


import com.github.sparkzxl.core.support.BaseUncheckedException;

/**
 * description: RPC降级异常
 *
 * @author zhouxinlei
 * @since 2025-09-05 13:47:55
 */
public class RpcFallbackException extends BaseUncheckedException {

    private static final long serialVersionUID = -7879378284877993323L;

    public RpcFallbackException(String errorCode, String errorMsg) {
        super(errorCode, errorMsg);
    }

}
