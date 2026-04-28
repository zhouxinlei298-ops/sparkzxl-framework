package com.github.sparkzxl.web.interceptor;

import com.github.sparkzxl.web.properties.InterceptorProperties;

import java.util.List;

/**
 * 工厂接口：管理 InnerInterceptor 的完整生命周期（加载、新增、移除、查询）
 *
 * @author zhouxinlei
 */
public interface InnerInterceptorFactory {

    /**
     * 初始化加载所有拦截器（SPI + YAML 配置）
     *
     * @return 拦截器列表
     */
    List<InnerInterceptor> loadInterceptors();

    /**
     * 通过 SPI 创建并新增拦截器
     *
     * @param name       SPI 名称
     * @param properties 拦截器配置
     */
    void addInterceptor(String name, InterceptorProperties properties);

    /**
     * 按 SPI 名称移除拦截器
     *
     * @param name SPI 名称
     */
    void removeInterceptor(String name);

    /**
     * 获取当前已加载的拦截器列表
     *
     * @return 拦截器列表（已排序）
     */
    List<InnerInterceptor> getInterceptors();
}
