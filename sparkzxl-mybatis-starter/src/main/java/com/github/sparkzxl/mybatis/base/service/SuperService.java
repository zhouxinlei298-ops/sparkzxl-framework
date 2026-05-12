package com.github.sparkzxl.mybatis.base.service;

import com.github.sparkzxl.mybatis.base.repository.SuperRepository;

/**
 * description: 基于MP的 IRepository 新增了4个方法： saveBatchSomeColumn、updateAllById、deletePhysical、deletePhysicalById
 * 1，saveBatchSomeColumn 批量插入
 * 2，updateAllById 执行后，会清除缓存
 * 2，deletePhysical 执行后，会物理删除
 * 2，deletePhysicalById 执行后，会物理删除
 *
 * @author zhouxinlei
 */
public interface SuperService<T> extends SuperRepository<T> {

}
