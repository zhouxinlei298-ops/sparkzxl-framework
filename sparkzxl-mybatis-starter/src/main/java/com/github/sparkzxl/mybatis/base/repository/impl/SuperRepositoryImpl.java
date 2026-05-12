package com.github.sparkzxl.mybatis.base.repository.impl;

import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import com.github.sparkzxl.mybatis.base.mapper.SuperMapper;
import com.github.sparkzxl.mybatis.base.repository.SuperRepository;

/**
 * description:
 *
 * @author zhouxinlei
 */
public class SuperRepositoryImpl<M extends SuperMapper<T>, T> extends CrudRepository<M, T> implements SuperRepository<T> {

    public SuperRepositoryImpl() {
    }
}
