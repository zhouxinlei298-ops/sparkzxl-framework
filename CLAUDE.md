# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在此代码库中工作时提供指导。

## 项目概述

`sparkzxl-framework` 是一个基于 DDD（领域驱动设计）原则的企业级 Spring Boot 框架。它提供了统一的 API 响应格式、封装了通用工具类，并集成了分布式系统组件。

**技术栈**: Java 21, Spring Boot 3.5.13, Spring Framework 6.2.17, Spring Cloud 2025.0.0, Spring Cloud Alibaba 2025.0.0.0

## 构建与开发命令

```bash
# 构建整个项目
mvn clean install

# 跳过测试构建
mvn clean install -DskipTests

# 构建并部署到 nexus（使用 release profile）
mvn clean deploy -P release

# 更新所有子模块版本号
mvn versions:set -DnewVersion=X.X.X

# 构建指定模块
mvn clean install -pl sparkzxl-core -am
```

## 模块架构

框架采用**多模块 Maven 结构**，职责清晰分离：

### 核心基础模块
- **sparkzxl-dependencies**: 集中式依赖管理（BOM pom）
- **sparkzxl-spi**: SPI（服务提供接口）抽象层
- **sparkzxl-annotation**: 自定义注解
- **sparkzxl-core**: 核心工具类、基础类、DDD 实体/服务模式
- **sparkzxl-common**: 共享通用组件

### 基础设施 Starters（`*-starter` 模式）
- **sparkzxl-boot-starter**: 核心启动配置
- **sparkzxl-web-starter**: Web 层，包含统一异常处理、拦截器
- **sparkzxl-cache-starter**: 多级缓存（Redis + Caffeine）
- **sparkzxl-mybatis-starter**: MyBatis Plus 集成，支持数据权限
- **sparkzxl-datasource-starter**: 动态数据源支持
- **sparkzxl-log-starter**: 结构化日志，集成 SkyWalking 追踪

### 分布式组件
- **sparkzxl-distributed-lock**: 分布式锁实现
  - `sparkzxl-distributed-lock-redis-starter`: 基于 Redis
  - `sparkzxl-distributed-lock-redisson-starter`: 基于 Redisson
  - `sparkzxl-distributed-lock-zookeeper-starter`: 基于 ZooKeeper
- **sparkzxl-distributed-cloud-starter**: Spring Cloud 集成
- **sparkzxl-distributed-dubbo-starter**: Dubbo RPC 支持

### RPC 通信（`sparkzxl-rpc`）
- `sparkzxl-feign-*`: OpenFeign 集成 Resilience4j/Hystrix
- `sparkzxl-dubbo-starter`: Dubbo 服务暴露/消费
- `sparkzxl-grpc-*`: gRPC 服务端/客户端启动器

### 设计模式（`sparkzxl-patterns-starter`）
- **策略模式**: `@Strategy` 注解实现自动策略注册
- **工厂模式**: 动态工厂 Bean 创建
- **管道模式**: 请求处理的责任链

### 安全与集成
- **sparkzxl-jwt-starter**: JWT 令牌处理
- **sparkzxl-oauth2-resource-starter**: OAuth2 资源服务器
- **sparkzxl-security-starter**: 安全工具
- **sparkzxl-signature**: 客户端/服务端签名验证
- **sparkzxl-data-scope**: 数据权限控制（租户隔离）

### 第三方集成
- **sparkzxl-oss**: 对象存储（阿里云、MinIO、RustFS）
- **sparkzxl-elasticsearch-starter**: ES 操作
- **sparkzxl-mongodb-starter**: MongoDB 集成
- **sparkzxl-sms-starter**: 短信服务（阿里云、腾讯云）
- **sparkzxl-mail-starter**: 邮件发送
- **sparkzxl-alarm**: 告警通知（钉钉、飞书、企业微信）

### 其他工具
- **sparkzxl-uid**: 分布式 ID 生成
- **sparkzxl-swagger-starter**: API 文档（Knife4j）
- **sparkzxl-sentinel-starter**: Sentinel 流量控制
- **sparkzxl-job-executor-starter**: XXL-Job 集成
- **sparkzxl-drools-starter**: 规则引擎

## 架构模式

### DDD 结构
框架在 `sparkzxl-core` 中实现 DDD 基础模式：
- **Entity**: 包含基础字段的领域实体
- **Service**: 分层服务接口
- **Repository**: 数据访问抽象

### 响应格式
所有 API 使用 `R<T>` 统一包装：
```java
public class R<T> {
    private int code;           // 响应编码
    private String message;     // 响应信息
    private boolean success;    // 是否成功
    private T result;           // 响应数据
    private String errorCode;   // 异常码
    private String errorMessage;// 异常信息
    private Map<String, Object> extra; // 附加数据(包含traceId)
}
```

### 异常处理
- `sparkzxl-web-starter` 中的全局异常处理器
- 自定义异常继承基础异常类
- 自动转换为统一错误响应

### 缓存策略
两级缓存（Caffeine L1 + Redis L2），支持：
- 缓存 Key 生成规则
- 自动序列化/反序列化
- TTL 和基于容量的淘汰策略

## 核心实现模式

### 策略模式使用
```java
@Strategy(serviceName = "orderPayStrategy", type = OrderPayType.WX_PAY)
public class WxPayStrategy implements IOrderPayStrategy {
    // 实现
}
```

### 数据权限插件
MyBatis Plus 多租户和数据隔离插件：
- 字段级租户隔离
- Schema 级隔离
- 数据源级隔离

### 分布式锁
基于模板的使用方式：
```java
@Autowired
private DistributedLockTemplate lockTemplate;

lockTemplate.execute(lockKey, () -> {
    // 被保护的代码
});
```

## 配置说明

- 多数据源配置使用 `spring.datasource.dynamic` 前缀
- 缓存配置使用 `sparkzxl.cache` 前缀
- 日志模式使用 `@tag` 注入追踪上下文
- SkyWalking APM toolkit 集成实现分布式追踪

## 版本管理

当前版本: `3.0.1-SNAPSHOT`

版本更新应使用：
```bash
mvn versions:set -DnewVersion=3.0.2-SNAPSHOT
```

所有模块通过 `${project.version}` 共享同一版本。

## 模块依赖规则

- Starter 模块不应依赖其他 Starter
- 核心模块（`sparkzxl-core`、`sparkzxl-common`）保持最小依赖
- `sparkzxl-dependencies` 是所有版本管理的 BOM
- 非必需功能使用 `optional` 依赖

## 开发环境配置

- **Maven 本地仓库**: `/Users/zhouxinlei/Documents/maven`
