package com.github.sparkzxl.mybatis.base.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.github.sparkzxl.core.entity.cache.CacheKey;
import com.github.sparkzxl.mybatis.base.repository.SuperCacheRepository;

import java.util.List;

/**
 * description: 缓存接口父类
 *
 * @author zhouxinlei
 */
public interface SuperCacheService<T> extends SuperCacheRepository<T> {

    T getByKey(CacheKey key, Wrapper<T> wrapper);

    List<T> listByKey(CacheKey key, Wrapper<T> wrapper);
}
