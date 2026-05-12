package com.github.sparkzxl.mybatis.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.github.sparkzxl.core.entity.cache.CacheKey;
import com.github.sparkzxl.mybatis.base.mapper.SuperMapper;
import com.github.sparkzxl.mybatis.base.repository.impl.SuperCacheRepositoryImpl;
import com.github.sparkzxl.mybatis.base.service.SuperCacheService;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * description: 缓存接口父类实现类
 * 默认的key规则： #{CacheKeyBuilder#key()}:id
 * <p>
 * 1，getByIdCache：新增的方法： 先查缓存，在查db
 * 2，removeById：重写 ServiceImpl 类的方法，删除db后，淘汰缓存
 * 3，removeByIds：重写 ServiceImpl 类的方法，删除db后，淘汰缓存
 * 4，updateAllById： 新增的方法： 修改数据（所有字段）后，淘汰缓存
 * 5，updateById：重写 ServiceImpl 类的方法，修改db后，淘汰缓存
 * </p>
 *
 * @param <M>
 * @param <T>
 * @author zhouxinlei
 */
public abstract class SuperCacheServiceImpl<M extends SuperMapper<T>, T> extends SuperCacheRepositoryImpl<M, T> implements SuperCacheService<T> {

    @Override
    public T getByKey(CacheKey key, Wrapper<T> wrapper) {
        Function<CacheKey, Object> loader = k -> getOne(wrapper);
        return super.getByKey(key,loader);
    }

    @Override
    public List<T> listByKey(CacheKey key, Wrapper<T> wrapper) {
        Function<CacheKey, Set<Object>> loader = k-> {
            List<T> list = list(wrapper);
            if (CollectionUtils.isNotEmpty(list)) {
                return new HashSet<>(list);
            }
            return Collections.emptySet();
        };
        return super.listByKey(key,loader);
    }
}
