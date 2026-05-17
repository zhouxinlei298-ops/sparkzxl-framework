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

    public void setPropagateHeaders(List<String> propagateHeaders) {
        if (CollectionUtils.isNotEmpty(propagateHeaders)) {
            for (String header : propagateHeaders) {
                if (!this.propagateHeaders.contains(header)) {
                    this.propagateHeaders.add(header);
                }
            }
        }
    }
}
