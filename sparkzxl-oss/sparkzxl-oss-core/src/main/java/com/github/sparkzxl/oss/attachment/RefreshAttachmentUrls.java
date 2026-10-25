package com.github.sparkzxl.oss.attachment;

import java.lang.annotation.*;

/**
 * 声明该实体类的实例出现在响应中时，其附件模型的短期访问地址由框架统一刷新。
 *
 * <p>标注在响应模型（VO/实体）类上；响应增强器在序列化前扫描响应图，对命中类的
 * 附件模型字段重新签发短期地址。刷新语义为「有值才生成」：模型已携带未过期地址时
 * 原样保留，只有稳定引用、地址缺失或即将过期时才向解析器请求新地址；无稳定引用的
 * 模型不生成任何地址。
 *
 * @author zhouxinlei
 * @since 2026-10-25
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RefreshAttachmentUrls {
}
