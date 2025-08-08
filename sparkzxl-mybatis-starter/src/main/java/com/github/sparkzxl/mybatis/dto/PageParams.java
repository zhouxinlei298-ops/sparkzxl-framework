package com.github.sparkzxl.mybatis.dto;

import com.github.sparkzxl.mybatis.constant.EntityConstant;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.HashMap;
import java.util.Map;

/**
 * description: 分页参数
 *
 * @author zhouxinlei
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "分页参数")
public class PageParams<T> extends PageDTO {

    @Schema(description = "查询参数", requiredMode = Schema.RequiredMode.REQUIRED)
    private T model;

    @Schema(description = "排序,默认createTime", allowableValues = "id,createTime,updateTime", example = "id")
    private String sort = EntityConstant.ID;

    @Schema(description = "扩展参数")
    private Map<String, String> extra = new HashMap<>(16);

}
