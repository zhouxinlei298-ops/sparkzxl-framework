package com.github.sparkzxl.oss.provider;

import com.github.sparkzxl.oss.properties.Configuration;

import java.util.Collections;
import java.util.List;

/**
 * description: 拒绝型配置提供者，供 {@code oss.register: none} 使用。
 *
 * <p>托管部署下配置完全由宿主在运行时提供（例如先调用
 * {@code OssExecutorFactoryContext.requireConfigurationSnapshot()} 建立快照）。该 provider 不读取
 * 环境变量、Nacos 或文件，也不回退任何默认客户端：静态加载入口一律明确拒绝，避免"看似可用的空配置"
 * 被静默使用。</p>
 *
 * @author zhouxinlei
 * @since 2026-10-16
 */
public class RejectingOssConfigProvider extends AbstractOssConfigProvider {

    /**
     * 拒绝信息；不包含目标地址、凭据或内部标识。
     */
    public static final String REJECTION_MESSAGE = "OSS configuration must be provided by the host publication";

    /**
     * {@inheritDoc}
     *
     * @param clientId 客户端标识（该模式下不参与判定）
     * @return 不会返回
     * @throws IllegalStateException 始终抛出：静态加载在该模式下不可用
     */
    @Override
    public Configuration load(String clientId) {
        throw new IllegalStateException(REJECTION_MESSAGE);
    }

    /**
     * {@inheritDoc}
     *
     * @return 空列表，表示本模式下没有任何静态客户端
     */
    @Override
    protected List<Configuration> list() {
        return Collections.emptyList();
    }
}
