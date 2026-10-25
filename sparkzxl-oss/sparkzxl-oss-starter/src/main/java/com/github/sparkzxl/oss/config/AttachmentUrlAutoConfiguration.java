package com.github.sparkzxl.oss.config;

import com.github.sparkzxl.oss.attachment.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 附件短期地址刷新自动装配：宿主提供身份与查表签发两个 SPI 后，框架装配默认解析器
 * 与响应刷新增强；宿主可用自定义 {@link AttachmentUrlResolver} 覆盖默认实现。
 *
 * @author zhouxinlei
 * @since 2026-10-25
 */
@Configuration(proxyBeanMethods = false)
public class AttachmentUrlAutoConfiguration {

    /**
     * 装配默认附件地址解析器。
     *
     * @param identityProvider 可信身份提供者
     * @param signer           附件查表签发端口
     * @return 默认解析器
     */
    @Bean
    @ConditionalOnBean({AttachmentIdentityProvider.class, AttachmentSigner.class})
    @ConditionalOnMissingBean(AttachmentUrlResolver.class)
    public AttachmentUrlResolver attachmentUrlResolver(
            AttachmentIdentityProvider identityProvider, AttachmentSigner signer) {
        return new DefaultAttachmentUrlResolver(identityProvider, signer);
    }

    /**
     * 装配响应刷新增强。
     *
     * @param resolver 附件地址解析器
     * @return 响应刷新增强
     */
    @Bean
    @ConditionalOnBean(AttachmentUrlResolver.class)
    @ConditionalOnMissingBean(AttachmentUrlResponseAdvice.class)
    public AttachmentUrlResponseAdvice attachmentUrlResponseAdvice(AttachmentUrlResolver resolver) {
        return new AttachmentUrlResponseAdvice(resolver);
    }
}
