package com.github.sparkzxl.datasource.properties;

import lombok.Getter;
import lombok.Setter;
import com.github.sparkzxl.datasource.listener.NacosWatchProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.Objects;

/**
 * description: nacos client Properties
 *
 * @author zhouxinlei
 * @since 2022-09-06 09:59:23
 */
@Getter
@Setter
public class NacosConsumerProperties {

    private String url;

    private String namespace;

    private String username;

    private String password;

    @NestedConfigurationProperty
    private NacosACMProperties acm;

    @NestedConfigurationProperty
    private NacosWatchProperties watchConfig;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NacosConsumerProperties that = (NacosConsumerProperties) o;
        return Objects.equals(url, that.url)
                && Objects.equals(namespace, that.namespace)
                && Objects.equals(username, that.username)
                && Objects.equals(password, that.password)
                && Objects.equals(acm, that.acm)
                && Objects.equals(watchConfig, that.watchConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, namespace, username, password, acm, watchConfig);
    }

    @Override
    public String toString() {
        return "NacosClientProperties{" +
                "url='" + url + '\'' +
                ", namespace='" + namespace + '\'' +
                ", username='" + username + '\'' +
                ", password='" + password + '\'' +
                ", acm=" + acm +
                ", watchConfig=" + watchConfig +
                '}';
    }
}
