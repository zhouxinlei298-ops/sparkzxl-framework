package com.github.sparkzxl.trace.properties;

import com.github.sparkzxl.core.constant.BaseContextConstants;
import com.google.common.collect.Lists;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.Serializable;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "sparkzxl.trace")
public class TraceProperties implements Serializable {

    private static final long serialVersionUID = -5090063299334539530L;

    private boolean enabled = true;

    private List<String> propagateHeaders = Lists.newArrayList(BaseContextConstants.TENANT_ID);

    private OtelProperties otel = new OtelProperties();

    public void setPropagateHeaders(List<String> propagateHeaders) {
        if (CollectionUtils.isNotEmpty(propagateHeaders)) {
            for (String header : propagateHeaders) {
                if (!this.propagateHeaders.contains(header)) {
                    this.propagateHeaders.add(header);
                }
            }
        }
    }

    @Data
    public static class OtelProperties implements Serializable {

        private static final long serialVersionUID = 1L;

        /** OTLP 导出地址（指向 OTel Collector 或 SkyWalking OAP） */
        private String endpoint = "http://localhost:4317";

        /** 服务名称，默认取 spring.application.name */
        private String serviceName;

        /** traces 导出器类型 */
        private String tracesExporter = "otlp";

        /** logs 导出器类型 */
        private String logsExporter = "none";

        /** metrics 导出器类型 */
        private String metricsExporter = "none";

        /** 是否启用 jedis 自动埋点 */
        private boolean jedisEnabled = false;

        /** 是否启用 logback 自动埋点 */
        private boolean logbackEnabled = true;
    }
}
