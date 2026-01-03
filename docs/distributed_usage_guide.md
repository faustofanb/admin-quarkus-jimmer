# 📡 Distributed Infrastructure Usage Guide

> 本文档面向 **Quarkus + Jimmer** 项目，详细说明如何使用项目中实现的分布式基础设施（锁、唯一 ID、幂等检查）以及统一的 `DistributedFacade` 门面。

---

## 目录

1. [概览](#概览)
2. [核心组件](#核心组件)
   - [分布式锁](#分布式锁)
   - [唯一 ID 生成（雪花算法）](#唯一-id-生成雪花算法)
   - [幂等检查](#幂等检查)
   - [统一门面 `DistributedFacade`](#统一门面-distributedfacade)
3. [配置 (`application.yaml`)](#配置-applicationyaml)
4. [使用示例](#使用示例)
   - [锁的使用](#锁的使用)
   - [ID 生成](#id-生成)
   - [幂等检查](#幂等检查使用)
   - [缓存防击穿（结合 CacheFacade）](#缓存防击穿结合-cachefacade)
5. [最佳实践 & 常见问题](#最佳实践--常见问题)
6. [扩展 & 定制](#扩展--定制)

---

## 概览

`io.github.faustofan.admin.shared.distributed` 包提供了一套 **分布式基础设施**，包括：

- **分布式锁**（基于 Redis 的 `SET NX EX` 实现）以及本地 JVM 锁，统一抽象为 `LockProvider`。
- **唯一 ID 生成**，采用 **雪花算法**（`SnowflakeIdGenerator`），支持解析时间戳、数据中心 ID、机器 ID、序列号。
- **幂等检查**，利用 Redis Key‑TTL 实现一次性请求防重复（`IdempotentChecker`）。
- **统一门面** `DistributedFacade`，对外统一暴露锁、ID、幂等等 API，业务层只需要注入该门面即可。

所有实现均遵循 **常量化、枚举化** 的原则，避免魔法字符串，配合 `DistributedConstants` 与 `DistributedConfig` 完全可配置化。

---

## 核心组件

### 分布式锁

| 接口/类 | 说明 |
|---------|------|
| `LockProvider` | 锁抽象，提供 `tryLock`、`unlock`、`executeWithLock` 等方法。 |
| `LocalLockProvider` | 基于 `java.util.concurrent.locks.ReentrantLock` 的本地锁实现（单实例）。 |
| `RedisLockProvider` | 基于 Redis `SET NX EX` 的分布式锁实现，支持等待、租约、强制解锁。 |
| `LockContext` | 锁上下文，记录锁键、持有者、获取时间、失效时间、锁类型。 |
| `LockType` (enum) | `LOCAL`、`REDIS`、`AUTO`（根据配置自动选择）。 |

### 唯一 ID 生成（雪花算法）

- **接口** `IdGenerator`：统一的 ID 生成 API（`nextId()`、`nextIdStr()`、`nextIds(int)`）。
- **实现** `SnowflakeIdGenerator`：基于 `DistributedConstants` 中的 `DATACENTER_ID_BITS`、`WORKER_ID_BITS`、`SEQUENCE_BITS` 计算唯一 64 位长整型 ID。
- **枚举** `IdGeneratorType`：目前仅 `SNOWFLAKE`，预留其他实现（如 UUID、数据库自增）。

### 幂等检查

- **接口** `IdempotentChecker`：提供 `check`、`checkAndMark`、`executeIfFirst`、`remove`、`generateToken` 等方法。
- **实现** `IdempotentStrategy`（基于 Redis `SETNX` + TTL）。
- **常量** `DistributedConstants.KeyPrefix.IDEMPOTENT` 用于统一前缀。

### 统一门面 `DistributedFacade`

```java
@Inject
DistributedFacade distributedFacade;
```

提供以下功能块：

| 功能 | 方法 | 说明 |
|------|------|------|
| **锁** | `getLockProvider()`、`tryLock(...)`、`executeWithLock(...)` | 根据 `DistributedConfig.lock.type` 自动选择本地或 Redis 锁。 |
| **ID** | `nextId()`、`nextIdStr()`、`nextIds(int)` | 雪花算法 ID 生成。 |
| **幂等** | `checkIdempotent(String)`、`checkAndMark(String)`、`executeIfFirst(String, Supplier<T>)` | 防止重复请求。 |
| **配置** | `isEnabled()`、`getConfig()` | 检查分布式功能是否开启。 |

---

## 配置 (`application.yaml`)

```yaml
app:
  distributed:
    enabled: true                     # 总开关，关闭后所有分布式功能失效
    lock:
      type: REDIS                     # LOCAL / REDIS / AUTO
      wait-time: PT10S                # 获取锁最大等待时间
      lease-time: PT30S               # 锁租约时间（自动过期）
      retry-interval: PT50MS          # 重试间隔
    id-generator:
      type: SNOWFLAKE                # 预留其他实现
      datacenter-id: 1                # 数据中心 ID（0~31）
      worker-id: 1                    # 机器 ID（0~31）
    idempotent:
      ttl: PT1H                       # 幂等键默认存活时间
      enabled: true                   # 是否启用幂等检查
    redis:
      enabled: true                   # 必须开启 Redis 才能使用分布式锁/幂等
      key-prefix: "admin:"            # 全局前缀，统一管理
```

> `DistributedConfig` 使用 Quarkus `@ConfigMapping` 将上述配置映射为强类型对象，业务代码通过 `config.lock().type()` 等方式读取。

---

## 使用示例

### 锁的使用

```java
// 注入 DistributedFacade
@Inject
DistributedFacade distributedFacade;

// 直接获取锁对象（自动根据配置选择）
LockProvider lockProvider = distributedFacade.getLockProvider();

// 简单的锁保护执行
boolean success = lockProvider.executeWithLock("order:create:123", () -> {
    orderService.createOrder(request);
});

// 带返回值的锁执行（自定义等待/租约）
Optional<Order> orderOpt = lockProvider.executeWithLock(
        "order:update:456",
        Duration.ofSeconds(5),   // 等待时间
        Duration.ofSeconds(30),  // 租约时间
        () -> orderService.updateOrder(request)
);
```

### ID 生成

```java
// 生成单个唯一 ID（Long）
long id = distributedFacade.nextId();

// 生成字符串形式（常用于业务编号）
String idStr = distributedFacade.nextIdStr();

// 批量生成
long[] ids = distributedFacade.nextIds(10);
```

### 幂等检查使用

```java
String token = "order:create:789";

// 仅在第一次请求时执行业务逻辑
Order order = distributedFacade.executeIfFirst(token, () -> orderService.createOrder(request));

// 手动检查并标记（适用于更复杂的业务）
if (distributedFacade.checkIdempotent(token)) {
    // 第一次请求，业务处理后标记
    orderService.createOrder(request);
    distributedFacade.checkAndMark(token); // 标记已处理
}
```

### 缓存防击穿（结合 CacheFacade）

```java
// 业务层只需要注入 CacheFacade 与 DistributedFacade
@Inject
CacheFacade cacheFacade;
@Inject
DistributedFacade distributedFacade;

public User getUser(Long userId) {
    String key = "user:" + userId;
    // 使用带锁的缓存读取，防止热点 key 同时穿透 DB
    return cacheFacade.getOrLoadWithLock(
            key,
            User.class,
            () -> userRepository.findById(userId),
            Duration.ofHours(1)
    );
}
```

---

## 最佳实践 & 常见问题

| 场景 | 建议 | 备注 |
|------|------|------|
| **锁竞争激烈** | 调整 `lock.wait-time` 与 `lock.retry-interval`，或改用 **本地锁**（`LockType.LOCAL`）在单实例环境下。 |
| **锁泄漏** | 确保 `executeWithLock` 包裹的代码块不抛出未捕获异常，或在 `finally` 中手动 `unlock`。 |
| **ID 冲突** | 确保 `datacenter-id` 与 `worker-id` 在整个集群中唯一（0~31），否则会产生重复 ID。 |
| **幂等键未失效** | 检查 `idempotent.ttl` 配置，TTL 过短可能导致业务未完成前键已失效。 |
| **Redis 连接异常** | `DistributedFacade.isEnabled()` 会返回 `false`，业务层可做降级处理（如直接执行业务而不做幂等检查）。 |

**日志调试**：打开 `io.github.faustofan.admin.shared.distributed` 包的 DEBUG 级别即可看到锁获取、释放、幂等检查等详细日志。

```properties
quarkus.log.category."io.github.faustofan.admin.shared.distributed".level=DEBUG
```

---

## 扩展 & 定制

1. **自定义锁实现**：实现 `LockProvider` 接口（例如基于 Zookeeper、Consul）并在 `DistributedConfig.lock.type` 中新增枚举值。
2. **其他 ID 生成器**：实现 `IdGenerator`（如基于 UUID、数据库序列），在 `IdGeneratorType` 中添加枚举并在 `DistributedFacade` 中根据配置返回对应实现。
3. **幂等策略**：如果业务对幂等有更复杂的需求（如多维度键、不同过期策略），可以在 `IdempotentStrategy` 中扩展 Redis 脚本或使用 Lua 原子操作。
4. **监控**：通过 Micrometer 将锁获取成功率、锁等待时长、幂等命中率等指标导出到 Prometheus。

```yaml
quarkus:
  micrometer:
    enabled: true
    export:
      prometheus:
        enabled: true
```

---


