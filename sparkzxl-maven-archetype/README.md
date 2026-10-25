# sparkzxl-maven-archetype

基于 [sparkzxl-framework](https://github.com/zhouxinlei828/sparkzxl-framework) **2.0.x（Spring Boot 2.7.18 / JDK 8）** 的 DDD 分层脚手架原型。
分层规范借鉴 spark-iam-server `authorization-server` 的实施经验（限界上下文 × 四层 + ArchUnit 守护），已合入框架仓库随版本统一演进。

> 3.x（JDK 21 / Spring Boot 3.5）线的原型在独立分支维护，待框架 3.x 线启动后合并到对应分支处理。

## 生成项目结构

```
${groupId}:${artifactId}
├── pom.xml                       # 聚合 pom，import sparkzxl-dependencies BOM
├── code-generator/               # MyBatis-Plus 代码生成器（按新分层落位生成代码）
└── ${artifactId}-server/         # 主应用（单 Maven 模块 + 包级 DDD）
    └── src/main/java/${package}/
        ├── bootstrap/            # 装配层：StartApplication、config、support/advice（全局异常）
        └── system/               # 示例限界上下文（复制此结构扩展新上下文）
            ├── interfaces/           # 仅 Controller
            ├── application/          # service(写)+query(读) 轻量CQRS、model(DTO/VO)、convert、port(端口)、event
            ├── domain/               # model(值对象)、repository(仓储接口，继承 SuperCacheService)
            └── infrastructure/       # repository/mapper/entity、adapter(端口实现)、cache(CacheKeyBuilder)
    └── src/test/java/${package}/architecture/
        ├── DDDLayerArchitectureTest       # 四层依赖方向守护
        └── ModelPlacementArchitectureTest # 模型落位守护（DTO/VO 不回 domain 等）
```

内置示例覆盖：`@ResponseResult` 统一响应、`@HttpRequestLog` 日志、`SuperCacheService` 主键缓存、
框架 `Entity` 审计基类（MetaDataHandler 自动填充 id/createdBy/createdAt/updatedBy/updatedAt）、
进程内领域事件、application/port 出站端口 + infrastructure/adapter 适配器、
项目级 `GlobalExceptionHandler`（只映射自定义异常，通用异常由框架 DefaultExceptionHandler 兜底）。

## 使用

```bash
# 1. 安装原型（框架仓库根目录）
mvn -pl sparkzxl-maven-archetype install -Dgpg.skip=true

# 2. 生成项目
mvn archetype:generate \
    -DarchetypeGroupId=com.github.sparkzxl \
    -DarchetypeArtifactId=sparkzxl-maven-archetype \
    -DarchetypeVersion=2.0.1-SNAPSHOT \
    -DgroupId=com.demo \
    -DartifactId=demo-service \
    -Dversion=1.0.0-SNAPSHOT \
    -Dpackage=com.demo \
    -DnacosAddr=127.0.0.1:8848 \
    -DnacosNamespace=none \
    -DdatabaseAddress=127.0.0.1:3306 \
    -DdatabaseName=demo \
    -DdatabaseUsername=root \
    -DdatabasePassword=123456 \
    -DsparkzxlDependenciesVersion=2.0.1-SNAPSHOT
```

`sparkzxlDependenciesVersion` 决定生成项目引用的 `sparkzxl-dependencies` BOM 版本，可在生成时覆盖。
`nacosNamespace` 传 `none` 表示使用 Nacos 公共命名空间（生成的配置会省略 namespace 项），使用独立命名空间时传其 ID。

环境要求：JDK 8、Maven 3.6+、MySQL 5.7+、Nacos 2.x、Redis。

## 本地运行

生成的项目自带 `docker-compose.yml`（MySQL + Redis + Nacos 一键起）与 `.gitignore`：

```bash
docker compose up -d          # 启动本地依赖（端口与骨架默认配置对齐）
# 执行下方"示例表 DDL"初始化 sys_user 表
mvn -f demo-service-server spring-boot:run
```

两个容易踩的点：

1. **审计字段的填充机制**：`created_by/updated_by` 由框架 `MetaDataHandler` 从请求上下文（`userid`、`name` 等属性，通常由网关从 JWT 解出后以请求头转发）读取填充。直接调接口不带这些头时创建人为空是**预期行为**，不是骨架缺陷。
2. **Nacos gRPC 端口**：nacos-client 2.x 除 8848 外还需要 9848/9849 端口可达，compose 已映射；自建 Nacos 时注意放行。

## 示例表 DDL

```sql
CREATE TABLE `sys_user`
(
    `id`         bigint(20) NOT NULL COMMENT '主键ID',
    `account`    varchar(64) NOT NULL COMMENT '账号',
    `password`   varchar(128) NOT NULL COMMENT '密码',
    `name`       varchar(64) DEFAULT NULL COMMENT '姓名',
    `status`     tinyint(1) DEFAULT '1' COMMENT '状态 1启用 0禁用',
    `is_delete`  tinyint(1) DEFAULT '0' COMMENT '删除状态（0：未删除，1：删除）',
    `created_by` bigint(20) DEFAULT NULL COMMENT '创建人id',
    `created_at` datetime DEFAULT NULL COMMENT '创建时间',
    `updated_by` bigint(20) DEFAULT NULL COMMENT '更新人id',
    `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统用户';
```

## 代码生成器

`code-generator` 模块的 `MybatisPlusGenerator` 在**工程根目录**运行（`user.dir` 需为工程根目录），
选择模块序号（1.system / 2.ext，即限界上下文）与表名后，按以下落位生成：

| 产物 | 落位 |
|---|---|
| Controller | `${package}.{module}.interfaces` |
| DTO / SaveDTO / UpdateDTO / VO | `${package}.{module}.application.model` |
| MapStruct Convert | `${package}.{module}.application.convert` |
| 应用服务接口/实现 | `${package}.{module}.application.service` |
| 仓储接口（继承 SuperCacheService） | `${package}.{module}.domain.repository` |
| 实体 / Mapper / 仓储实现 | `${package}.{module}.infrastructure.*` |

## 框架组件索引

骨架默认只引入 `boot-starter`（聚合 web/log/mybatis/datasource/swagger/core）、`cache-starter`、`distributed-cloud-starter`（nacos + feign）。
其余能力按需在 pom 中追加，版本均由 `sparkzxl-dependencies` BOM 管理：

| 能力 | 坐标（groupId 均为 com.github.sparkzxl） | 说明 |
|---|---|---|
| 统一响应/全局异常/undertow | sparkzxl-web-starter（已随 boot 引入） | `@ResponseResult`、`R`、`DefaultExceptionHandler` |
| 操作日志/审计 | sparkzxl-log-starter（已随 boot 引入） | `@HttpRequestLog`、`@OptLogRecord` |
| 登录用户参数解析 | sparkzxl-user-starter | `@EnableLoginUser` + Controller 直接注入当前用户 |
| 认证 | sparkzxl-jwt-starter / sparkzxl-oauth2-resource-starter | JWT 校验 / OAuth2 资源服务 |
| 对象存储 | sparkzxl-oss-starter + oss-executor-minio/aliyun/rustfs-starter | 含附件短期(签名)地址刷新 |
| 告警通知 | sparkzxl-alarm-starter（dingtalk/feishutalk/wetalk 可单选） | AlarmClient 统一入口 |
| 短信/邮件 | sparkzxl-sms-starter / sparkzxl-mail-starter | |
| 分布式锁 | sparkzxl-distributed-lock-redis/redisson/zookeeper-starter | |
| 分布式 ID | sparkzxl-uid（已随 boot 引入） | 实体主键自动生成 |
| RPC | sparkzxl-distributed-dubbo-starter / sparkzxl-rpc 下 dubbo、feign、grpc 子模块 | |
| 熔断限流 | sparkzxl-sentinel-starter | |
| 任务调度 | sparkzxl-job-executor-starter | xxl-job 执行器 |
| 数据权限 | sparkzxl-data-scope-starter | |
| 配置/数据同步 | sparkzxl-data-sync-nacos/zookeeper/websocket/provider | |
| 多数据源增强 | sparkzxl-datasource-starter（已随 boot 引入） | druid、dynamic-datasource、p6spy |
| 搜索/文档库 | sparkzxl-elasticsearch-starter / sparkzxl-mongodb-starter | |
| 规则引擎 | sparkzxl-drools-starter | |
| 接口签名（国密 SM3） | sparkzxl-signature-client / signature-server | |
| 策略模式骨架 | sparkzxl-patterns-starter | |
| 网关插件 | sparkzxl-gateway-plugin-autoconfigure | 网关侧过滤器/鉴权扩展 |

## 与框架的持续兼容

本模块的 Maven 集成测试（`src/test/resources/projects/basic`，goal 为 `verify`）会在 `mvn install` 时
从原型生成示例工程并完整编译 + 执行 ArchUnit 守护测试。框架任何 starter 变更若破坏了生成项目的可编译性，
构建会当场失败——原型因此随框架自动保鲜，无需人工对齐。
