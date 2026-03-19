package com.github.sparkzxl.dubbo.support;


import lombok.Getter;

/**
 * description: dubbo异常传递
 *
 * @author zhouxinlei
 * @since 2025-09-05 13:47:55
 */
@Getter
public class DubboTransferException extends RuntimeException {

    private static final long serialVersionUID = 2045159066276433148L;
    private final String serviceName;
    private final String exClassName;
    private final String message;

    public DubboTransferException(String serviceName,
                                  String exClassName,
                                  String message) {
        super(message);
        this.serviceName = serviceName;
        this.exClassName = exClassName;
        this.message = message;
    }

}
