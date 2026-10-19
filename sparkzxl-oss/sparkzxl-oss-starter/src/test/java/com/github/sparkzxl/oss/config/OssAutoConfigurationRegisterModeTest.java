package com.github.sparkzxl.oss.config;

import com.github.sparkzxl.oss.executor.OssExecutorFactoryContext;
import com.github.sparkzxl.oss.provider.JdbcOssConfigProvider;
import com.github.sparkzxl.oss.provider.OssConfigProvider;
import com.github.sparkzxl.oss.provider.RejectingOssConfigProvider;
import org.junit.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 注册模式的装配矩阵：{@code none} 为加法，其余模式行为与失败信息不得改变。
 *
 * @author zhouxinlei
 * @since 2026-10-16
 */
public class OssAutoConfigurationRegisterModeTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OssAutoConfiguration.class));

    @Test
    public void noneRegistersRejectingProviderAndKeepsFactoryAssembled() {
        runner.withPropertyValues("oss.register=none").run(context -> {
            assertTrue("context must start without oss.configs", context.getStartupFailure() == null);
            OssConfigProvider provider = context.getBean(OssConfigProvider.class);
            assertTrue(provider instanceof RejectingOssConfigProvider);
            assertNotNull(context.getBean(OssExecutorFactoryContext.class));
            try {
                provider.loadConfigurationList();
            } catch (RuntimeException exception) {
                fail("loadConfigurationList must not throw: " + exception.getMessage());
            }
            try {
                provider.load("any-client");
                fail("rejecting provider must not return a configuration");
            } catch (IllegalStateException exception) {
                assertEquals(RejectingOssConfigProvider.REJECTION_MESSAGE, exception.getMessage());
            }
        });
    }

    @Test
    public void missingRegisterModeStillFailsFast() {
        runner.run(context -> {
            Throwable failure = context.getStartupFailure();
            assertNotNull(failure);
            assertTrue(messages(failure).contains("store cannot be empty."));
        });
    }

    @Test
    public void yamlModeWithEmptyConfigsStillFailsFast() {
        runner.withPropertyValues("oss.register=yaml").run(context -> {
            Throwable failure = context.getStartupFailure();
            assertNotNull(failure);
            assertTrue(messages(failure).contains("In yaml mode, configList cannot be empty."));
        });
    }

    @Test
    public void jdbcModeKeepsItsProvider() {
        runner.withPropertyValues("oss.register=jdbc").run(context -> {
            assertTrue(context.getStartupFailure() == null);
            assertTrue(context.getBean(OssConfigProvider.class) instanceof JdbcOssConfigProvider);
        });
    }

    @Test
    public void noneWithOssDisabledDoesNotAssembleTheStarter() {
        runner.withPropertyValues("oss.register=none", "oss.enabled=false").run(context -> {
            assertTrue(context.getStartupFailure() == null);
            assertFalse(context.containsBean("ossExecutorFactoryContext"));
        });
    }

    private String messages(Throwable failure) {
        StringBuilder builder = new StringBuilder();
        Throwable cursor = failure;
        while (cursor != null) {
            if (cursor.getMessage() != null) {
                builder.append(cursor.getMessage()).append(' ');
            }
            cursor = cursor.getCause();
        }
        return builder.toString();
    }
}
