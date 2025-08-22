package com.github.sparkzxl.datasource.listener;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import com.github.sparkzxl.datasource.properties.DynamicDataProperties;
import com.github.sparkzxl.datasource.properties.NacosConsumerProperties;

import java.util.*;
import java.util.concurrent.Executor;

/**
 * description:
 *
 * @author zhouxinlei
 * @since 2025-08-21 15:23:35
 */
@Slf4j
public class NacosDynamicDataSourceConfigListener implements ConfigListener {

    private final ConfigService configService;
    private final DataSourceConfigOnChange dataSourceConfigOnChange;
    protected static final Map<String, List<Listener>> LISTENERS = Maps.newConcurrentMap();
    private final Map<String, String> watchConfigMap = Maps.newConcurrentMap();

    public NacosDynamicDataSourceConfigListener(DynamicDataProperties dynamicDataProperties,
                                                DataSourceConfigOnChange dataSourceConfigOnChange) throws NacosException {

        Properties properties = new Properties();
        DynamicDataProperties.Listener listener = dynamicDataProperties.getListener();
        NacosConsumerProperties nacosConsumerProperties = listener.getNacos();
        if (nacosConsumerProperties.getAcm() != null && nacosConsumerProperties.getAcm().isEnabled()) {
            properties.put(PropertyKeyConst.ENDPOINT, nacosConsumerProperties.getAcm().getEndpoint());
            properties.put(PropertyKeyConst.NAMESPACE, nacosConsumerProperties.getAcm().getNamespace());
            properties.put(PropertyKeyConst.ACCESS_KEY, nacosConsumerProperties.getAcm().getAccessKey());
            properties.put(PropertyKeyConst.SECRET_KEY, nacosConsumerProperties.getAcm().getSecretKey());
        } else {
            properties.put(PropertyKeyConst.SERVER_ADDR, nacosConsumerProperties.getUrl());
            if (StringUtils.isNotBlank(nacosConsumerProperties.getNamespace())) {
                properties.put(PropertyKeyConst.NAMESPACE, nacosConsumerProperties.getNamespace());
            }
            if (nacosConsumerProperties.getUsername() != null) {
                properties.put(PropertyKeyConst.USERNAME, nacosConsumerProperties.getUsername());
            }
            if (nacosConsumerProperties.getPassword() != null) {
                properties.put(PropertyKeyConst.PASSWORD, nacosConsumerProperties.getPassword());
            }
        }
        this.configService = NacosFactory.createConfigService(properties);
        this.dataSourceConfigOnChange = dataSourceConfigOnChange;
        DynamicDataProperties.Listener listenerConfig = dynamicDataProperties.getListener();
        NacosConsumerProperties watchProperties = listenerConfig.getNacos();
        watchConfigMap.put(watchProperties.getWatchConfig().getDataId(), watchProperties.getWatchConfig().getGroup());
        start();
    }

    @Override
    public void start() {
        for (Map.Entry<String, String> entry : watchConfigMap.entrySet()) {
            watcherData(entry.getKey(), entry.getValue(), dataSourceConfigOnChange);
        }
    }


    protected void watcherData(final String dataId, final String group, final OnChange oc) {
        Listener listener = new Listener() {

            @Override
            public void receiveConfigInfo(final String configInfo) {
                oc.change(false, configInfo);
            }

            @Override
            public Executor getExecutor() {
                return null;
            }
        };
        oc.change(true, getConfigAndSignListener(dataId, group, listener));
        LISTENERS.computeIfAbsent(dataId, key -> new ArrayList<>()).add(listener);
    }

    private String getConfigAndSignListener(final String dataId, final String group, final Listener listener) {
        String config = null;
        try {
            config = configService.getConfigAndSignListener(dataId, group, 6000, listener);
        } catch (NacosException e) {
            log.error(e.getMessage(), e);
        }
        if (Objects.isNull(config)) {
            config = "";
        }
        return config;
    }

    @Override
    public void close() {
        LISTENERS.forEach((dataId, lss) -> {
            lss.forEach(listener -> {
                String group = watchConfigMap.get(dataId);
                configService.removeListener(dataId, group, listener);
            });
            lss.clear();
        });
        LISTENERS.clear();
    }
}
