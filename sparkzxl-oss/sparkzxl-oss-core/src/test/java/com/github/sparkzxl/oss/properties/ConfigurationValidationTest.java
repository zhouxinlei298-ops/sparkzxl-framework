package com.github.sparkzxl.oss.properties;

import static org.junit.Assert.*;

import com.github.sparkzxl.oss.client.OssClient;
import com.github.sparkzxl.oss.creator.OssClientCreator;
import com.github.sparkzxl.oss.creator.OssClientFactory;

import org.junit.Test;

import java.util.Collections;

/**
 * 配置必填参数与可选字段边界回归。
 *
 * @author zhouxinlei
 * @since 2026-10-07
 */
public class ConfigurationValidationTest {
    private Configuration valid() {
        Configuration c = new Configuration();
        c.setClientType("partner_s3");
        c.setEndpoint("https://storage.example");
        c.setAccessKey("test-access");
        c.setSecretKey("test-secret");
        c.setBucketName("attachments");
        return c;
    }

    @Test
    public void optionalFieldsMayBeMissing() {
        Configuration c = valid();
        c.setRegion(null);
        c.setDomain(null);
        c.setObjectPrefix(null);
        c.setFileFormat(null);
        c.validateClientConfiguration();
        c.setRegion("");
        c.setDomain("");
        c.setObjectPrefix("");
        c.setFileFormat("");
        c.validateClientConfiguration();
    }

    @Test
    public void rejectsMissingCredentialsWithoutExposingSecrets() {
        for (String missing : new String[] {null, "", " "}) {
            Configuration c = valid();
            c.setAccessKey(missing);
            IllegalArgumentException error =
                    assertThrows(IllegalArgumentException.class, c::validateClientConfiguration);
            assertFalse(error.getMessage().contains("test-secret"));
            c = valid();
            c.setSecretKey(missing);
            error = assertThrows(IllegalArgumentException.class, c::validateClientConfiguration);
            assertFalse(error.getMessage().contains("test-access"));
        }
    }

    @Test
    public void rejectsInvalidEndpointAndOptionalDomain() {
        for (String value :
                new String[] {
                    null,
                    "",
                    " ",
                    "storage.example",
                    "ftp://storage.example",
                    "https://user:password@storage.example"
                }) {
            Configuration c = valid();
            c.setEndpoint(value);
            assertThrows(IllegalArgumentException.class, c::validateClientConfiguration);
        }
        Configuration c = valid();
        c.setDomain("invalid");
        assertThrows(IllegalArgumentException.class, c::validateClientConfiguration);
    }

    @Test
    public void rejectsMissingBucket() {
        Configuration c = valid();
        c.setBucketName(" ");
        assertThrows(IllegalArgumentException.class, c::validateClientConfiguration);
    }

    @Test
    public void factoryRejectsInvalidConfigurationBeforeCallingProvider() {
        final boolean[] called = {false};
        OssClientCreator creator =
                new OssClientCreator() {
                    public String supportClientType() {
                        return "partner_s3";
                    }

                    public OssClient<?> createClient(Configuration c) {
                        called[0] = true;
                        return null;
                    }
                };
        OssClientFactory factory = new OssClientFactory(Collections.singletonList(creator));
        Configuration c = valid();
        c.setSecretKey(null);
        assertThrows(IllegalArgumentException.class, () -> factory.buildOssClient(c));
        assertFalse(called[0]);
        factory.buildOssClient(valid());
        assertTrue(called[0]);
    }
}
