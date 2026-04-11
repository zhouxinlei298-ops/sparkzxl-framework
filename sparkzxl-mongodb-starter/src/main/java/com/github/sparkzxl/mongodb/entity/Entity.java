package com.github.sparkzxl.mongodb.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * description: 公共属性
 *
 * @author zhouxinlei
 */
@Getter
@Setter
public class Entity<E> implements Serializable {

    private static final long serialVersionUID = 7602513313454068898L;
    @Id
    @Indexed(unique = true)
    private E id;

    @CreatedBy
    public String createdBy;
    @Field(value = "createName")
    public String createName;
    @CreatedDate
    public LocalDateTime createdAt;

    @LastModifiedBy
    public String updatedBy;
    @Field(value = "updateName")
    public String updateName;
    @LastModifiedDate
    public LocalDateTime updatedAt;

}
