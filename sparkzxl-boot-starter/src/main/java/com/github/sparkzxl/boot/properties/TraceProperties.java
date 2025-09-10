package com.github.sparkzxl.boot.properties;

import com.github.sparkzxl.core.constant.enums.TraceTypeEnum;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.Serializable;

/**
 * description: 链路追踪配置类
 *
 * @author zhouxinlei
 * @since 2025-08-30 13:46:16
 */
@Data
@ConfigurationProperties(prefix = "spring.trace")
public class TraceProperties implements Serializable {

    private static final long serialVersionUID = -5090063299334539530L;
    private boolean enabled = true;

    private TraceTypeEnum type = TraceTypeEnum.LOCAL;
}
