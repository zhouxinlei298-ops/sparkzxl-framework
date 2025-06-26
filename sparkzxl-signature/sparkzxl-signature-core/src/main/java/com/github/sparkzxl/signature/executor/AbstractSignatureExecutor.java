package com.github.sparkzxl.signature.executor;

import com.github.sparkzxl.core.util.ArgumentAssert;
import com.github.sparkzxl.signature.algorithm.SignAlgorithm;
import com.github.sparkzxl.signature.algorithm.SignAlgorithmContext;
import com.github.sparkzxl.signature.properties.SignatureProperties;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

/**
 * description:
 *
 * @author zhouxinlei
 * @since 2024-05-20 10:28:20
 */

@SuppressWarnings(value = "all")
public abstract class AbstractSignatureExecutor<T> implements SignatureExecutor<T> {

    @Autowired
    private SignatureProperties signatureProperties;
    @Autowired
    private SignAlgorithmContext signAlgorithmContext;

    public SignatureProperties.AppProperties getConfigByTenantId(String tenantId) {
        Map<String, SignatureProperties.AppProperties> provider = signatureProperties.getConfigMap();
        SignatureProperties.AppProperties properties = provider.get(tenantId);
        ArgumentAssert.notNull(properties, "租户[{}]应用签名配置不存在", tenantId);
        return properties;
    }

    public SignAlgorithm getSignAlgorithm(String type) {
        return signAlgorithmContext.getSignAlgorithm(type);
    }
}
