package com.github.sparkzxl.log.entity;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * description: 操作日志实体类
 *
 * @author zhouxinlei
 */
@Data
@Accessors(chain = true)
public class OptRecordLog implements Serializable {

    private static final long serialVersionUID = -3827211216877219021L;
    /**
     * 请求IP
     */
    private String ip;

    /**
     * 请求接口
     */
    private String requestUrl;

    /**
     * 业务对象标识
     */
    private String bizNo;

    /**
     * 操作日志的种类
     */
    private String category;

    /**
     * 日志详情
     */
    private String detail;

    /**
     * 异常信息
     */
    private String errorMsg;

    /**
     * 操作人id
     */
    private String operatorId;

    /**
     * 操作人
     */
    private String operator;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 完成时间
     */
    private LocalDateTime finishTime;

    /**
     * 耗时
     */
    private Long consumeTime;

    /**
     * 租户
     */
    private String tenantId;

    /**
     * 追踪ID
     */
    private String traceId;

}
