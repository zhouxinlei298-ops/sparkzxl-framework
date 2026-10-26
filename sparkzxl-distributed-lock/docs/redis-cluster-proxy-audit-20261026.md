# 分布式锁 Redis 实现阿里云集群 Proxy 模式合规审计与改造报告

日期：2026-10-26
范围：`sparkzxl-distributed-lock`（core / redis-starter / redisson-starter / zookeeper-starter）
依据：`spark-iam` 仓 `docs/guides/redis-cluster-lua-restrictions.md`（阿里云集群 Proxy 九条限制 + 四条硬规则，2026-10-26 登录事故后基线；spark-iam-server 有镜像副本）
结论：**生产生效的 RedisTemplateLockExecutor 两个脚本修复后全部合规**，双测试（静态断言 + 真连接四路径）在 cluster 模式容器全绿。

## 1. 生产 classpath 生效链路（待查清问题 1）

- `spark-iam-server/authorization-server/pom.xml` 直接依赖 `sparkzxl-distributed-lock-redis-starter`
  （版本随 `sparkzxl-dependencies 2.0.1-SNAPSHOT`，即本仓库）。
- 全仓 pom 无 `redisson`、无 `sparkzxl-distributed-lock-redisson-starter`、无 zookeeper 锁依赖。
  任务前稿提到的 `sparkzxl-n-redis-starter` 在两仓所有 pom 中均不存在（历史名，实际直接引用 redis-starter）。
- 本地 bootstrap/properties.yaml 及仓内全部配置无 `distributed-lock.primaryExecutor` 覆盖；
  `LockTemplate.afterPropertiesSet` 中 primaryExecutor=null 时取 `executors.get(0)`，
  而 classpath 上唯一的执行器即 `RedisTemplateLockExecutor`。

**结论：spark-iam-server 生产生效执行器 = RedisTemplateLockExecutor（StringRedisTemplate + 两个内联 Lua 脚本）。Redisson 执行器不在生产 classpath，仅作框架级兼容性审计。**

## 2. LockTemplate 调用方清单（spark-iam-server，2026-10-26 全量）

| 调用方 | 场景 | expire / acquireTimeout | 备注 |
| --- | --- | --- | --- |
| `ApolloSsoSessionPolicyResolver` | SSO 策略加载单飞（登录路径） | 30s / 3s | 锁不可用时降级读本地缓存（stale-grace） |
| `ApolloApplicationTokenPolicyResolver` | 应用 token 策略加载单飞 | 30s / 3s | 同上，降级兜底 |
| `EnterpriseCacheJob` | 企业缓存定时刷新互斥 | 30min / 0（单次尝试） | 拿不到锁直接跳过本轮 |
| `DatabaseOssPublicationQueue` | OSS 发布目标互斥 | 5min / 3s | |
| `ProtectionBulkTaskExecutionService` | — | — | 明确不叠加 LockTemplate（自有轮询） |

其他业务线（apollo-purchase / apollo-quota / apollo-dataflow 等）存在 `@DistributedLock` AOP 用法，
随各自发版同样受本模块脚本约束，不在本次 spark-iam-server 范围内逐一列出。

## 3. 九条限制 × 现有实现逐条结论

生产生效脚本：`RedisTemplateLockExecutor.SCRIPT_LOCK`（SET NX PX）与 `SCRIPT_UNLOCK`（GET 校验 + DEL）。

| # | 限制 | RedisTemplateLockExecutor（生产） | RedissonLockExecutor 3.17.1（不在生产 classpath，参照审计） |
| --- | --- | --- | --- |
| 1 | KEYS 必须字面量下标 | ✅ 两脚本均仅 `KEYS[1]` 字面量 | ✅ Redisson 官方脚本字面量 |
| 2 | 调用必须带 key | ✅ 各传 1 键 | ✅ 单键 |
| 3 | 多键必须同 hash tag | ✅ 单键不适用 | ✅ 单键 |
| 4 | SCRIPT LOAD 不跨节点同步 | ✅ `DefaultRedisScript` EVALSHA→EVAL 自动回退 | ✅ Redisson 自带回退 |
| 5 | Lua 内禁 pub/sub | ✅ 无 | ✅ 解锁通知 pub/sub 在 Lua 外（代理允许普通 pub/sub；Redisson 订阅语义依赖该能力，接入前仍需在灰度实测） |
| 6 | 禁 UNPACK | ✅ 无 | ✅ |
| 7 | MULTI 内禁 EVAL | ✅ 无 MULTI 用法 | ✅ |
| 8 | Lua 内禁跨节点命令 | ✅ 无 | ✅ |
| 9 | 小版本过低禁 EVAL | n/a（生产已可执行 EVAL） | n/a |

四条硬规则核对：

| 硬规则 | 修复前 | 修复后 |
| --- | --- | --- |
| 缺键守卫 | ⚠️ `redis.call('get',KEYS[1]) == ARGV[1]`：缺键时 GET 返回 false，`false == 字符串` 恒不等走 else 返回 'false'——**行为正确但隐式依赖跨类型比较**，不符合规范「显式拦截」范式 | ✅ `local current=redis.call('get',KEYS[1]); if current==false then return 0 end; if current~=ARGV[1] then return 0 end; return redis.call('del',KEYS[1])` |
| 数字文本化 | ✅ `String.valueOf(expire)` 传 ARGV[2]（沿用） | ✅ 不变 |
| KEYS 字面量 | ✅ 沿用 | ✅ 不变（新增静态断言锁死） |
| 同 tag | ✅ 单键 | ✅ 不变（见第 5 节 tag 结论） |

返回值约定同步对齐：unlock 由返回 `'true'/'false'` 字符串 + `Boolean.parseBoolean` 改为 **Long 1/0**
（`RedisScript<Long>`，resultSerializer 传 null——Long 结果不经序列化器解码），与 iam 侧脚本约定一致；
`releaseLock` 以 `releaseResult == 1L` 判定。

## 4. 硬性双测试（管理规范第 3 节，不缺项）

均位于 `sparkzxl-distributed-lock-redis-starter/src/test/java/com/github/sparkzxl/lock/executor/`（与被测脚本同模块同包）：

1. **静态断言** `RedisTemplateLockScriptsClusterSafetyTest`（4 用例，常开）：
   - `KEYS[...]` 全部字面量下标（正则锁死，防回归代理静态校验拒绝）；
   - unlock 含显式 `==false` 缺键守卫、无 `get(KEYS[1])==ARGV[1]` 裸比较；
   - 脚本内无 pub/sub、UNPACK、MULTI/EXEC、KEYS/SCAN/RANDOMKEY；
   - 返回值类型约定（unlock=Long、lock=String）。
2. **真连接行为** `RedisTemplateLockExecutorRealBehaviorTest`（4 用例，`@EnabledIfEnvironmentVariable(named="IAM_REAL_REDIS_HOST")` 门控，未设自动跳过）：
   - 首次加锁：键不存在时 SET NX PX 成功，Redis 中值=持有者、TTL≤expire；
   - 重复加锁冲突：他人已持锁时 acquire 返回 null，原值不被覆盖；
   - 释放：非持有者释放被拒（CAS 未命中）；持有者释放成功且键删除；键已删后再释放走缺键路径返回 false；
   - 锁过期后重入：expire 1.5s 到期后新持有者可加锁，旧持有者释放被拒。

验证环境：`redis-cluster-verify`（redis:8.6.1，cluster 模式，宿主 6390，密码 123456），
`JAVA_HOME=zulu-8`，`IAM_REAL_REDIS_HOST=127.0.0.1 IAM_REAL_REDIS_PORT=6390 IAM_REAL_REDIS_PASSWORD=123456`。
结果：**8/8 通过；去掉环境变量后真连接 4 例自动 skip、静态断言照常执行**。

```bash
docker start redis-cluster-verify
cd /Users/zhouxinlei/IdeaProjects/sparkzxl-framework && \
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home \
IAM_REAL_REDIS_HOST=127.0.0.1 IAM_REAL_REDIS_PORT=6390 IAM_REAL_REDIS_PASSWORD=123456 \
mvn -pl sparkzxl-distributed-lock/sparkzxl-distributed-lock-redis-starter -am test
```

## 5. 关键问题结论（待查清问题 2/3/4）

1. **RedisTemplate 续期缺位（renewal）**：`AbstractLockExecutor.renewal()` 默认 false，RedisTemplate
   执行器不续期，expire 到期锁即失效——**持锁方任务超过 expire 后互斥即被打破，并发可闯入**。
   现状评估：4 个生产调用方均显式传 expire（30s/5min/30min），临界区为策略查询/缓存刷新，远短于
   expire；SSO/token 两侧锁不可用时还有 stale 缓存降级兜底，当前无实际越界场景。
   **本期不引入看门狗**（接口变更 + 调度生命周期管理 + 需灰度验证，超出 Proxy 合规改造范围）。
   后续若出现长任务持锁需求：优先 **fencing token 范式**（参考 spark-iam
   `RedisFlowSessionLeaseStore`：主键+租约键+计数键三键同 tag，INCR 单调 token，旧执行者写入被拒），
   其次才是续期键方案（届时锁键族必须整体加 hash tag，见下条）。
2. **锁键 hash tag**：**不加**。单键锁无 slot 约束；且改键格式会导致滚动发布窗口内新旧实例使用不同键名、
   互斥失效——比不加密得多。约束记录：未来若出现「主锁+续期键/计数键」组合脚本，整族键必须同 tag
   （范式见 `RedisFlowSessionLeaseStore.slotKey`：`{sessionId}` tag 派生三键）。
3. **Redisson forceUnlock 风险**：`isHeldByCurrentThread()` 检查通过后到 `forceUnlockAsync` 执行之间，
   锁可能恰好过期并被他人持有，forceUnlock 不校验持有者会**强删对方的锁**。已改为 `unlockAsync()`
   （Redisson 内部 Lua 校验持有者，过期/非持有时拒绝并抛 IllegalMonitorStateException，捕获记日志返回
   false）。该执行器不在 spark-iam-server 生产 classpath，改动不影响当前生产，属防御性加固。
   Redisson 3.17.1 与阿里云 Proxy 的完整兼容性（pub/sub 解锁通知、看门狗）未实测，接入前需按灰度闸门验证。

## 6. 改动清单与发布闸门

改动（feature-2.0.1）：
- `RedisTemplateLockExecutor`：unlock 脚本显式缺键守卫 + Long 1/0 返回约定（行为兼容：持有者释放语义不变）；
- `RedissonLockExecutor`：`forceUnlockAsync`→`unlockAsync` + 中断位恢复；
- redis-starter pom 增 `spring-boot-starter-test`（test scope）；
- 新增双测试两个类（见第 4 节）。

发布顺序：框架 2.0.1-SNAPSHOT 发版 → spark-iam-server 重新打包 → 先发与生产同类 Redis
（阿里云集群 Proxy）的灰度环境，真实跑通加锁场景（SSO 策略加载单飞、企业缓存刷新、OSS 发布）→ 再上生产。
unlock 脚本文本变更会导致服务端脚本缓存未命中，EVALSHA→EVAL 自动回退装载，无需人工预热。
