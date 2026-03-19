package com.github.sparkzxl.dubbo.support;


import lombok.Getter;

/**
 * description: dubbo服务超时异常
 *
 * @author zhouxinlei
 * @since 2025-09-05 13:47:55
 */
@Getter
public class ServiceException extends RuntimeException {

    private static final long serialVersionUID = 7481005172963931731L;
    private final String errorCode;
    private final String message;

    public ServiceException(String errorCode,
                            String message) {
        super(message);
        this.errorCode = errorCode;
        this.message = message;
    }

}
