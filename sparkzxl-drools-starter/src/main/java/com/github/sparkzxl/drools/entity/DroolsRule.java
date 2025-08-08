package com.github.sparkzxl.drools.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * description: drools 规则父类
 *
 * @author zhouxinlei
 */
@Data
@Schema(description = "drools规则父类")
public class DroolsRule {

    /**
     * 分组
     */
    @Schema(description = "分组")
    private String agendaGroup;
    /**
     * 结果查询
     */
    @Schema(description = "结果查询")
    private String resultQuery;
    /**
     * 结果属性对象
     */
    @Schema(description = "结果属性对象")
    private String resultVariable;

    /**
     * 类名
     */
    @Schema(description = "结果类名")
    private String className;

}

