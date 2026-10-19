package com.github.sparkzxl.oss.provider;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 拒绝型配置提供者契约：只做拒绝，不回落任何静态来源。
 *
 * @author zhouxinlei
 * @since 2026-10-16
 */
public class RejectingOssConfigProviderTest {

    private final RejectingOssConfigProvider provider = new RejectingOssConfigProvider();

    @Test
    public void loadRejectsWithStableMessage() {
        try {
            provider.load("any-client");
            fail("rejecting provider must not return a configuration");
        } catch (IllegalStateException exception) {
            assertEquals(RejectingOssConfigProvider.REJECTION_MESSAGE, exception.getMessage());
        }
    }

    @Test
    public void loadRejectsForBlankClientIdAsWell() {
        try {
            provider.load(null);
            fail("rejecting provider must not return a configuration");
        } catch (IllegalStateException exception) {
            assertEquals(RejectingOssConfigProvider.REJECTION_MESSAGE, exception.getMessage());
        }
    }

    @Test
    public void loadConfigurationListIsEmptyAndDoesNotThrow() {
        assertNotNull(provider.loadConfigurationList());
        assertTrue(provider.loadConfigurationList().isEmpty());
    }

    @Test
    public void rejectionMessageDoesNotLeakTargetOrCredentials() {
        String message = RejectingOssConfigProvider.REJECTION_MESSAGE.toLowerCase();
        assertTrue(!message.contains("http") && !message.contains("accesskey")
                && !message.contains("secretkey") && !message.contains("endpoint")
                && !message.contains("="));
    }
}
