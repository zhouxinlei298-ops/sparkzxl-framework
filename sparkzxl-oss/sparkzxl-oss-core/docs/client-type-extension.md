# OSS供应商类型扩展

框架内置minio、aliyun、rustfs，保留现有客户端与执行器实现。OssClientType.of(value, displayName)供消费方定义扩展类型；标识为小写字母开头、最多16字符，后续允许数字、下划线及连字符。类型身份只由value决定，展示名不参与equals/hashCode。JSON仍为字符串，Configuration.clientType与发布快照字段仍使用字符串，不改变现有数据库与报文格式。

消费方扩展步骤：

1. 在消费方代码定义类型常量，例如OssClientType.of("partner_s3", "合作方对象存储")。框架不预置消费方供应商定义。
2. 实现OssClientCreator，supportClientType()返回该常量value()，将实例注册到OssClientFactory；clientType()默认桥接现有字符串方法。同标识由不同创建器重复注册会报错。
3. 实现对应OssClient、OssExecutor及OssExecutorFactory，按现有ExtensionLoader约定在META-INF的OssExecutorFactory资源中登记同一标识。仅注册客户端创建器不足以形成完整运行能力。
4. 消费方配置或可信发布报文使用同一字符串标识。快照解析只验证标识语法；不存在已注册实现时仍拒绝运行，不回退内置供应商。

内置常量不会安装插件或改变已发布配置。框架没有消费方供应商目录或可动态覆盖的全局类型注册表。此次未修改Center消费方的供应商管理选项、数据库约束或发布授权规则；消费方控制面是否允许扩展类型，需要其单独明确。

## 端到端扩展验证

测试中使用`partner_s3`夹具证明消费方扩展路径：`OssClientType`仅校验并保留标识，`OssClientCreator`按该标识注册客户端创建器，`META-INF/<OssExecutorFactory接口全名>`中的同名键通过ExtensionLoader解析执行器工厂。该夹具只存在于测试源码，不是框架内置或推荐的生产供应商定义。

未注册客户端创建器或未登记执行器SPI时仍拒绝运行；同标识由不同创建器重复注册也会失败。2026-10-07完整回归：Sparkzxl OSS core 36项、Phoenix OSS starter 45项均零失败/错误。该证据不包含真实第三方账号、桶操作或消费方控制面授权。
