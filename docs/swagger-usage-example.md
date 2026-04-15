# Swagger 全局响应配置使用示例

## 功能说明

`globalResponseCustomizer` 会自动将 API 响应包装成统一的 `R<T>` 格式，但只对带有 `@ResponseResult` 注解的类或方法生效。

## 启用响应包装

### 方式一：在 Controller 类上添加注解

```java
import com.github.sparkzxl.web.annotation.ResponseResult;
import io.swagger.v3.oas.annotations.Operation;

@ResponseResult  // ✅ 在类上添加注解，所有方法都会被包装
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Operation(summary = "查询用户列表")
    @PostMapping("/list")
    public List<UserVO> list() {
        return userService.list();
    }

    // 这个方法也会被包装，因为类上有 @ResponseResult 注解
    @Operation(summary = "查询用户详情")
    @GetMapping("/get")
    public UserVO get(@RequestParam Long id) {
        return userService.getById(id);
    }
}
```

### 方式二：在具体方法上添加注解

```java
import com.github.sparkzxl.web.annotation.ResponseResult;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Operation(summary = "查询用户列表")
    @ResponseResult  // ✅ 只在方法上添加注解，只有这个方法会被包装
    @PostMapping("/list")
    public List<UserVO> list() {
        return userService.list();
    }

    // ❌ 这个方法不会被包装，因为没有 @ResponseResult 注解
    @Operation(summary = "查询用户详情")
    @GetMapping("/get")
    public UserVO get(@RequestParam Long id) {
        return userService.getById(id);
    }
}
```

## 排除特定方法

如果类上有 `@ResponseResult` 注解，但想排除某些方法不被包装，可以使用 `@IgnoreResponseWrap` 注解：

```java
import com.github.sparkzxl.web.annotation.IgnoreResponseWrap;
import com.github.sparkzxl.web.annotation.ResponseResult;
import io.swagger.v3.oas.annotations.Operation;

@ResponseResult  // 类级别注解
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Operation(summary = "查询用户列表")
    @PostMapping("/list")
    public List<UserVO> list() {
        return userService.list();
    }

    @IgnoreResponseWrap  // ✅ 排除这个方法，不进行包装
    @Operation(summary = "下载文件")
    @GetMapping("/download")
    public void download(HttpServletResponse response) {
        // 直接返回文件流，不包装成 R<T>
    }
}
```

## 使用 CurdController

对于继承 `CurdController` 或 `SuperController` 的 Controller，同样需要添加 `@ResponseResult` 注解：

```java
import com.github.sparkzxl.mybatis.base.controller.SuperController;
import com.github.sparkzxl.web.annotation.ResponseResult;
import io.swagger.v3.oas.annotations.tags.Tag;

@ResponseResult  // ✅ 启用响应包装
@Tag(name = "数据源配置", description = "数据源配置管理")
@RestController
@RequestMapping("/datasource")
public class DatasourceConfigController extends SuperController<
        DatasourceConfigService,
        Long,
        DatasourceConfig,
        DatasourceConfigSaveDTO,
        DatasourceConfigUpdateDTO,
        DatasourceConfigQueryDTO,
        DatasourceConfigExcelDTO> {

    // 继承的所有 CRUD 方法（save, update, delete, get, page, list 等）
    // 都会自动被包装成 R<T> 格式
}
```

## 响应格式

启用响应包装后，所有接口的响应格式为：

```json
{
  "code": 200,
  "message": "请求成功",
  "success": true,
  "result": [
    {
      "id": 1,
      "name": "张三",
      "email": "zhangsan@example.com"
    }
  ],
  "errorCode": null,
  "errorMessage": null,
  "extra": {
    "traceId": "1234567890abcdef"
  }
}
```

## 错误响应

错误响应也会自动添加到文档中（400, 401, 403, 404, 500）：

```json
{
  "code": 401,
  "message": "用户登录已过期",
  "success": false,
  "result": null,
  "errorCode": "A0230",
  "errorMessage": "用户登陆已过期",
  "extra": {
    "traceId": "example-trace-id"
  }
}
```

## 注意事项

1. **必须添加注解**：如果不添加 `@ResponseResult` 注解，响应不会被包装成 `R<T>` 格式
2. **@IgnoreResponseWrap 优先级高**：即使类上有 `@ResponseResult`，方法上的 `@IgnoreResponseWrap` 也会生效
3. **与 ResponseResultAdvice 配合**：Swagger 文档中的响应包装逻辑与运行时的 `ResponseResultAdvice` 保持一致
4. **原始 Schema 保留**：包装后的响应中，`result` 字段会保留原始接口定义的 Schema（如 `List<UserVO>`）
