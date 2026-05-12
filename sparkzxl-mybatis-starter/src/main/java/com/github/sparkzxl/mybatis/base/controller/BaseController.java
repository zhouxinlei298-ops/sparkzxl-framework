package com.github.sparkzxl.mybatis.base.controller;

import com.github.sparkzxl.mybatis.base.repository.SuperRepository;

/**
 * description: 基础controller
 *
 * @author zhouxinlei
 */
public interface BaseController<Entity> {

    /**
     * 获取实体的类型
     *
     * @return Class<Entity>
     */
    Class<Entity> getEntityClass();

    /**
     * 获取Service
     *
     * @return SuperService<Entity>
     */
    SuperRepository<Entity> getBaseService();

}
