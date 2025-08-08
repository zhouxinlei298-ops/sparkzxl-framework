package com.github.sparkzxl.mybatis.dto;

import jakarta.validation.constraints.Max;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * description: 分页入参
 *
 * @author zhouxinlei
 */
@Data
public class PageDTO {

    @Schema(description = "当前页", example = "1")
    @Min(1)
    private Integer pageNum;

    @Schema(description = "分页大小", example = "10")
    @Max(value = 2000)
    private Integer pageSize;

}
