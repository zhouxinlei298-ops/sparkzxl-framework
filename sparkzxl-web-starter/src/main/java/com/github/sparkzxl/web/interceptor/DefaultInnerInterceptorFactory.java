package com.github.sparkzxl.web.interceptor;

import cn.hutool.core.text.StrFormatter;
import com.github.sparkzxl.spi.ExtensionLoader;
import com.github.sparkzxl.web.properties.InterceptorProperties;
import com.github.sparkzxl.web.properties.WebProperties;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 默认工厂实现：通过 SPI ExtensionLoader 管理 InnerInterceptor 的完整生命周期。
 * 持有 WebProperties 引用，支持配置热更新。
 *
 * @author zhouxinlei
 */
@Slf4j
public class DefaultInnerInterceptorFactory implements InnerInterceptorFactory {

    private final WebProperties webProperties;

    private final List<InnerInterceptor> interceptorList = Lists.newArrayList();

    public DefaultInnerInterceptorFactory(WebProperties webProperties) {
        this.webProperties = webProperties;
        log.info("DefaultInnerInterceptorFactory 实例化, hashCode={}", this.hashCode());
    }

    @Override
    public List<InnerInterceptor> loadInterceptors() {
        interceptorList.clear();

        List<InterceptorProperties> configList = webProperties.getInterceptorConfigList();
        // 默认加载 RequestContextInnerInterceptor（通过 SPI key "context"）
        InnerInterceptor contextInterceptor = ExtensionLoader
                .getExtensionLoader(InnerInterceptor.class).getJoin("context");
        InterceptorProperties defaultProperties = new InterceptorProperties();
        defaultProperties.setIncludePatterns(Lists.newArrayList("/**"));
        contextInterceptor.initInnerInterceptor(defaultProperties);
        interceptorList.add(contextInterceptor);

        // 通过 SPI 加载 YAML 配置的其他拦截器
        Map<String, InterceptorProperties> interceptorPropertiesMap = configList.stream()
                .collect(Collectors.toMap(InterceptorProperties::getName, k -> k));
        for (Map.Entry<String, InterceptorProperties> entry : interceptorPropertiesMap.entrySet()) {
            InnerInterceptor innerInterceptor = ExtensionLoader
                    .getExtensionLoader(InnerInterceptor.class).getJoin(entry.getKey());
            if (innerInterceptor == null) {
                throw new RuntimeException(StrFormatter.format("未装配[{}]", entry.getKey()));
            }
            innerInterceptor.initInnerInterceptor(entry.getValue());
            interceptorList.add(innerInterceptor);
        }

        sort();
        return interceptorList;
    }

    @Override
    public void addInterceptor(String name, InterceptorProperties properties) {
        InnerInterceptor innerInterceptor = ExtensionLoader
                .getExtensionLoader(InnerInterceptor.class).getJoin(name);
        if (innerInterceptor == null) {
            throw new RuntimeException(StrFormatter.format("未装配[{}]", name));
        }
        innerInterceptor.initInnerInterceptor(properties);
        interceptorList.add(innerInterceptor);
        sort();
    }

    @Override
    public void removeInterceptor(String name) {
        interceptorList.removeIf(interceptor -> interceptor.named().equals(name));
        sort();
    }

    @Override
    public List<InnerInterceptor> getInterceptors() {
        return interceptorList;
    }

    private void sort() {
        interceptorList.sort(Comparator.comparing(Ordered::getOrder));
    }
}
