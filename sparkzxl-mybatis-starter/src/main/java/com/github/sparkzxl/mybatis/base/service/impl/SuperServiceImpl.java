package com.github.sparkzxl.mybatis.base.service.impl;

import com.github.sparkzxl.mybatis.base.mapper.SuperMapper;
import com.github.sparkzxl.mybatis.base.repository.impl.SuperRepositoryImpl;

/**
 * description:
 *
 * @author zhouxinlei
 */
public class SuperServiceImpl<M extends SuperMapper<T>, T> extends SuperRepositoryImpl<M, T> {

    public SuperServiceImpl() {
    }
}
