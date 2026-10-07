# OSS SDK 职责边界

框架提供供应商类型扩展、客户端创建、配置刷新、操作域、上传下载及签名链接。业务侧负责配置监听、租户用途绑定、读写授权、发布版本与文档有效期、凭据引用解析。

`OssPublishedRuntime`、`OssRuntimeSnapshot`、`OssSnapshotCodec` 及 `secret` 包已从框架移除。Apollo 实现迁入 `apollo-user-center` 的 `administration.platform.infrastructure.oss.runtime`、`.publication` 和 `.secret` 包。框架不依赖 Apollo 发布文档，也不规定 `env:`、`file:`、`sealed:` 格式。

配置监听完成后，由宿主将解析完成的 `Configuration` 集合交给 `OssExecutorFactoryContext.refresh(version, configurations)`；普通配置提供器方式仍可用。需要等待首次完整配置的宿主应在任何操作前调用 `requireConfigurationSnapshot()`，避免回退读取其他来源。

工厂只检查客户端配置一致性、版本单调性及操作资源生命周期。配置有效期和业务授权在宿主操作入口校验，禁止业务代码绕过该入口。刷新失败保留原有完整配置，在途操作继续使用原客户端至引用释放。

这属于未交付 OSS 改造的 API 收敛。引用已移除类型或三参数 `refresh` 的消费代码需要迁移；框架不保留 Apollo 专用兼容层。当前 `apollo-file`、`apollo-dataflow` 按用户要求冻结，本次不修改，恢复开发后需适配这些 API，届时才能升级依赖。
