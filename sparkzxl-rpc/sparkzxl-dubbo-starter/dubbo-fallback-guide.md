# Sparkzxl Dubbo 服务降级使用指南

## 概述

Sparkzxl Dubbo Starter 提供了一套完整的服务降级解决方案，通过声明式注解简化降级逻辑的注册和管理。

### 核心特性

- **服务级降级**：支持服务级降级策略配置
- **声明式配置**：通过 `@DubboFallback` 注解简化降级处理器注册，同时支持类级别和方法级别
- **函数式编程**：方法级别注解支持 Lambda 表达式，简化降级逻辑编写
- **异常自动转换**：将 RpcException 自动转换为友好的 RpcFallbackException
- **全局异常处理**：自动捕获降级异常并返回统一格式的响应

## 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                    Dubbo 服务调用                             │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
         ┌───────────────────────┐
         │  服务调用是否成功？     │
         └───────────┬───────────┘
                     │
            ┌────────┴────────┐
            │                 │
           Yes               No
            │                 │
            │                 ▼
            │      ┌──────────────────────┐
            │      │ 获取降级处理器         │
            │      │ (服务级 > 默认降级)     │
            │      └──────────┬───────────┘
            │                 │
            │                 ▼
            │      ┌──────────────────────┐
            │      │ 执行降级逻辑           │
            │      │ 返回默认值/缓存值       │
            │      └──────────┬───────────┘
            │                 │
            └─────────────────┤
                              ▼
                   ┌─────────────────┐
                   │  返回调用结果     │
                   └─────────────────┘
```

## 核心组件

| 组件 | 说明 |
|------|------|
| `@DubboFallback` | 降级处理器声明注解 |
| `DubboFallbackHandler` | 降级处理器函数式接口 |
| `DubboFallbackRegistry` | 降级处理器注册中心 |
| `DefaultDubboFallbackHandler` | 默认降级处理器（兜底） |
| `DubboFallbackAnnotationProcessor` | 注解处理器 |
| `RpcFallbackException` | RPC降级异常 |
| `DubboExceptionHandler` | 全局异常处理器 |

## 快速开始

### 1. 类级别注解

适用于整个服务的所有方法调用失败时使用统一的降级逻辑。

```java
@Component
@DubboFallback(value = "userService", interfaceClass = UserService.class)
public class UserServiceFallback implements DubboFallbackHandler {

    @Override
    public Result handle(Invoker<?> invoker, Invocation invocation, RpcException exception, String clientName) {
        // clientName = "userService"，用于日志标识
        log.info("服务降级: client={}", clientName);

        // 返回降级结果（根据业务需求返回合适的值）
        // 方式1：返回 null
        // return AsyncRpcResult.newDefaultAsyncResult(null, null, invocation);

        // 方式2：抛出自定义降级异常（会被全局异常处理器捕获）
        RpcFallbackException fallbackException = new RpcFallbackException(
            "SERVICE_UNAVAILABLE",
            "服务暂时不可用，请稍后重试"
        );
        return AsyncRpcResult.newDefaultAsyncResult(null, fallbackException, invocation);
    }
}
```

### 2. 方法级别注解（Lambda 支持）

`@DubboFallback` 注解支持标注在方法上，允许使用 Lambda 表达式简化降级逻辑。

```java
@Configuration
public class FallbackConfig {

    @Bean
    @DubboFallback(value = "orderService", interfaceClass = OrderService.class)
    public DubboFallbackHandler orderServiceFallback() {
        return (invoker, invocation, exception, clientName) -> {
            // clientName = "orderService"，用于日志标识
            log.info("服务降级: client={}", clientName);

            // 返回降级结果
            RpcFallbackException fallbackException = new RpcFallbackException(
                "ORDER_SERVICE_BUSY",
                "订单服务繁忙，请稍后重试"
            );
            return AsyncRpcResult.newDefaultAsyncResult(null, fallbackException, invocation);
        };
    }
}
```

### 3. 编程式注册

手动调用 `DubboFallbackRegistry` 进行降级处理器注册（适用于需要动态注册的场景）。

```java
@Configuration
public class FallbackConfig {

    @PostConstruct
    public void registerFallbacks() {
        DubboFallbackRegistry.registerService(
            "com.example.service.ProductService",
            "productService",
            (invoker, invocation, exception, clientName) -> {
                log.info("产品服务降级: client={}", clientName);
                // 返回降级结果
                RpcFallbackException fallbackException = new RpcFallbackException(
                    "PRODUCT_SERVICE_UNAVAILABLE",
                    "产品服务暂时不可用"
                );
                return AsyncRpcResult.newDefaultAsyncResult(null, fallbackException, invocation);
            }
        );
    }
}
```

## 设计说明

### 注解属性

| 属性 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `value` | String | 是 | 客户端/服务名称，用于日志标识和问题排查，不参与降级匹配 |
| `interfaceClass` | Class<?> | 是 | 服务接口类，用于降级匹配（通过接口全限定名） |

### 降级匹配逻辑

```
通过 interfaceClass 接口名匹配 → 使用对应的降级处理器 → 执行 handle() 方法
```

- 一个接口只能配置一个降级处理器，多次注册会覆盖
- `value` 属性仅用于日志追踪，不影响降级匹配

### 降级处理建议

| 场景 | 降级策略 |
|------|----------|
| 读服务 | 抛出 `RpcFallbackException`，返回友好的错误信息 |
| 写服务 | 抛出 `RpcFallbackException`，引导用户重试 |
| 计算服务 | 抛出 `RpcFallbackException`，返回近似值或上次结果 |
| 第三方服务 | 抛出 `RpcFallbackException`，使用本地备份或 Mock 数据 |

## 默认降级行为

当没有配置自定义降级处理器时，`DefaultDubboFallbackHandler` 会根据 RpcException 类型返回友好的错误信息：

| 异常类型 | 错误码 | 错误信息 |
|---------|--------|----------|
| FORBIDDEN_EXCEPTION (No provider) | OPEN_SERVICE_UNAVAILABLE | 服务不可用 |
| TIMEOUT_EXCEPTION | TIME_OUT_ERROR | 调用超时 |
| NETWORK_EXCEPTION | FAILURE | 网络异常 |
| 其他 | RPC_SERVICE_EXCEPTION | 调用异常 |

## 全局异常处理

`DubboExceptionHandler` 自动捕获 `RpcFallbackException` 并返回统一格式的响应：

```java
@ControllerAdvice
public class DubboExceptionHandler {

    @ExceptionHandler(RpcFallbackException.class)
    public R<?> handleRpcFallbackException(RpcFallbackException e) {
        return R.failDetail(e.getErrorCode(), e.getMessage());
    }
}
```

响应格式：
```json
{
    "code": "OPEN_SERVICE_UNAVAILABLE",
    "message": "[com.example.service.UserService#getUserById]服务不可用，请稍后重试",
    "success": false
}
```

## 高级用法

### 1. 降级 + 缓存

```java
@Component
@DubboFallback(value = "configService", interfaceClass = ConfigService.class)
public class ConfigServiceFallback implements DubboFallbackHandler {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Result handle(Invoker<?> invoker, Invocation invocation, RpcException exception, String clientName) {
        // 尝试从缓存获取
        String cacheKey = "config:" + invocation.getMethodName();
        Object cachedValue = redisTemplate.opsForValue().get(cacheKey);

        if (cachedValue != null) {
            return AsyncRpcResult.newDefaultAsyncResult(cachedValue, null, invocation);
        }

        // 缓存未命中，抛出降级异常
        RpcFallbackException fallbackException = new RpcFallbackException(
            "CONFIG_UNAVAILABLE",
            "配置服务暂时不可用"
        );
        return AsyncRpcResult.newDefaultAsyncResult(null, fallbackException, invocation);
    }
}
```

### 2. 降级 + 限流

```java
@Component
@DubboFallback(value = "hotService", interfaceClass = HotService.class)
public class HotServiceFallback implements DubboFallbackHandler {

    private final RateLimiter rateLimiter = RateLimiter.create(100);

    @Override
    public Result handle(Invoker<?> invoker, Invocation invocation, RpcException exception, String clientName) {
        if (rateLimiter.tryAcquire()) {
            // 允许通过，抛出降级异常
            RpcFallbackException fallbackException = new RpcFallbackException(
                "SERVICE_DEGRADED",
                "服务繁忙，已进入降级模式"
            );
            return AsyncRpcResult.newDefaultAsyncResult(null, fallbackException, invocation);
        } else {
            // 拒绝请求
            RpcFallbackException fallbackException = new RpcFallbackException(
                "RATE_LIMIT",
                "服务繁忙，请稍后重试"
            );
            return AsyncRpcResult.newDefaultAsyncResult(null, fallbackException, invocation);
        }
    }
}
```

### 3. 动态降级配置

```java
@Component
@DubboFallback(value = "dynamicService", interfaceClass = DynamicService.class)
public class DynamicServiceFallback implements DubboFallbackHandler {

    @Value("${fallback.enabled:true}")
    private boolean fallbackEnabled;

    @Override
    public Result handle(Invoker<?> invoker, Invocation invocation, RpcException exception, String clientName) {
        if (!fallbackEnabled) {
            // 降级开关关闭，直接抛出原始异常
            throw exception;
        }

        // 降级开关开启，抛出降级异常
        RpcFallbackException fallbackException = new RpcFallbackException(
            "SERVICE_DEGRADED",
            "服务暂时不可用"
        );
        return AsyncRpcResult.newDefaultAsyncResult(null, fallbackException, invocation);
    }
}
```

## 最佳实践

### 1. 降级原则

- **快速失败**：降级逻辑应该简单快速，避免引入新的依赖和复杂性
- **友好提示**：返回清晰的错误信息，便于用户理解和处理
- **记录日志**：降级发生时必须记录详细日志，便于问题排查
- **监控告警**：降级频率过高时应该触发告警

### 2. 注意事项

1. **避免降级级联**：降级逻辑中不要调用其他可能失败的远程服务
2. **降级幂等性**：确保降级逻辑的幂等性，避免重复执行带来副作用
3. **资源隔离**：降级逻辑使用的资源（如线程池）应与主业务隔离
4. **定期演练**：定期进行降级演练，确保降级机制有效性

## 配置说明

### 自动配置

Dubbo 服务降级通过 `DubboFallbackAutoConfiguration` 自动启用，无需额外配置。

### 手动禁用

如需禁用降级功能，可以通过 Spring Boot 配置排除自动配置：

```yaml
spring:
  autoconfigure:
    exclude:
      - org.phoenix.dubbo.config.DubboFallbackAutoConfiguration
```

## 工具方法

```java
// 检查是否存在服务级降级处理器
boolean hasFallback = DubboFallbackRegistry.hasServiceFallback(
    "com.example.service.UserService"
);

// 手动执行降级（自动选择降级处理器）
Result result = DubboFallbackRegistry.executeFallback(
    invoker, invocation, exception
);
```

## 故障排查

### 降级未生效

1. 检查 `@DubboFallback` 注解的 `interfaceClass` 是否正确
2. 检查 `value` 属性是否已填写
3. 确认降级处理器实现了 `DubboFallbackHandler` 接口
4. 查看日志中是否有注册成功的提示信息

### 降级异常被吞

1. 检查 `DubboExceptionHandler` 是否被正确加载
2. 确认异常处理器的优先级配置
3. 查看是否有其他全局异常处理器优先捕获了异常

## 变更日志

| 版本 | 日期 | 说明 |
|------|------|------|
| 1.2.0 | 2026-03-20 | 简化设计，移除方法级降级，保留核心服务级降级功能 |
| 1.1.0 | 2026-03-20 | 新增方法级别注解支持，支持 Lambda 表达式自动扫描 |
| 1.0.0 | 2026-03-20 | 初始版本，支持基础降级功能 |
