package com.github.sparkzxl.trace.properties;

import com.github.sparkzxl.core.trace.TraceTypeEnum;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "sparkzxl.trace")
public class TraceProperties implements Serializable {

    private static final long serialVersionUID = -5090063299334539530L;

    private boolean enabled = true;

    private TraceTypeEnum type = TraceTypeEnum.LOCAL;

    /** 可选传递的上下文参数列表（不含 traceId，traceId 始终自动传播） */
    private List<String> propagateHeaders = Arrays.asList(
            "tenantId", "userId", "account", "name", "version", "token"
    );
}
