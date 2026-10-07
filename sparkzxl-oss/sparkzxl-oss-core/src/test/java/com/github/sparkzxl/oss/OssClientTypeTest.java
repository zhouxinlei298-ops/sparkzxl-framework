package com.github.sparkzxl.oss;

import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.sparkzxl.oss.client.OssClient;
import com.github.sparkzxl.oss.creator.OssClientCreator;
import com.github.sparkzxl.oss.creator.OssClientFactory;
import com.github.sparkzxl.oss.executor.OssExecutorFactory;
import com.github.sparkzxl.oss.executor.OssExecutorFactoryContext;
import com.github.sparkzxl.oss.properties.Configuration;

import org.junit.Test;

import java.util.Collections;

/**
 * 内置兼容、扩展标识及创建器注册边界验证。
 *
 * @author zhouxinlei
 * @since 2026-10-07
 */
public class OssClientTypeTest {
    /** 内置标识及JSON字符串保持兼容。 */
    @Test
    public void preservesBuiltinsAndStringWireFormat() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        assertSame(OssClientType.MINIO, OssClientType.of("minio"));
        assertEquals("\"aliyun\"", mapper.writeValueAsString(OssClientType.ALIYUN));
        assertEquals(OssClientType.RUSTFS, mapper.readValue("\"rustfs\"", OssClientType.class));
    }

    /** 消费方扩展类型按标识比较并可往返序列化。 */
    @Test
    public void extensionIdentityDoesNotDependOnDisplayName() throws Exception {
        OssClientType custom = OssClientType.of("partner_s3", "消费方供应商");
        assertEquals(custom, OssClientType.of("partner_s3"));
        assertEquals(custom.hashCode(), OssClientType.of("partner_s3").hashCode());
        ObjectMapper mapper = new ObjectMapper();
        assertEquals(
                custom, mapper.readValue(mapper.writeValueAsString(custom), OssClientType.class));
    }

    /** 拒绝大小写别名、空白及不合法标识。 */
    @Test
    public void rejectsAmbiguousOrInvalidIdentifiers() {
        for (String value :
                new String[] {null, "", " minio", "MINIO", "a/b", "0123", "abcdefghijklmnopq"}) {
            assertThrows(IllegalArgumentException.class, () -> OssClientType.of(value));
        }
        assertThrows(IllegalArgumentException.class, () -> OssClientType.of("partner", " "));
    }

    /** 消费方执行器工厂通过同一SPI标识解析，证明扩展不依赖内置名单。 */
    @Test
    public void consumerExecutorFactoryUsesSameExtensionIdentifier() {
        OssExecutorFactory factory = OssExecutorFactoryContext.newInstance("partner_s3");

        assertSame(PartnerS3OssExecutorFactory.class, factory.getClass());
    }

    /** 消费方创建器可注册，重复注册冲突与缺失实现拒绝执行。 */
    @Test
    public void registeredConsumerCreatorIsUsedAndDuplicatesRejected() {
        final Configuration[] captured = new Configuration[1];
        OssClientCreator custom =
                new OssClientCreator() {
                    public String supportClientType() {
                        return "partner_s3";
                    }

                    public OssClient<?> createClient(Configuration configuration) {
                        captured[0] = configuration;
                        return null;
                    }
                };
        OssClientFactory factory = new OssClientFactory(Collections.singletonList(custom));
        factory.registerCreator(custom);
        Configuration configuration = new Configuration();
        configuration.setClientType("partner_s3");
        configuration.setEndpoint("https://storage.example");
        configuration.setAccessKey("test-access");
        configuration.setSecretKey("test-secret");
        factory.buildOssClient(configuration);
        assertSame(configuration, captured[0]);
        assertEquals(OssClientType.of("partner_s3"), custom.clientType());
        assertThrows(
                IllegalStateException.class,
                () ->
                        factory.registerCreator(
                                new OssClientCreator() {
                                    public String supportClientType() {
                                        return "partner_s3";
                                    }

                                    public OssClient<?> createClient(Configuration configuration) {
                                        return null;
                                    }
                                }));
        configuration.setClientType("unregistered");
        assertThrows(
                UnsupportedOperationException.class, () -> factory.buildOssClient(configuration));
    }
}
