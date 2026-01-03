# 📦 Admin 系统缓存使用指南

本指南面向 **Quarkus + Jimmer** 项目，详细说明如何在业务代码中使用我们实现的 **二级缓存**（本地 L1 + Redis L2），以及如何通过配置、策略、防护机制（防穿透、雪崩、击穿）来提升系统性能与可靠性。

---

## 目录

1. [快速上手](#快速上手)  
2. [缓存结构概览](#缓存结构概览)  
3. [核心 API（CacheFacade）](#核心-api-cachefacade)  
4. [缓存策略（CacheStrategy）](#缓存策略-cachestrategy)  
5. [防护机制](#防护机制)  
   - 防穿透（空值占位）  
   - 防雪崩（TTL 随机抖动）  
   - 防击穿（布隆过滤 + 分布式锁）  
6. [配置示例（application.yaml）](#配置示例-applicationyaml)  
7. [常见问题 & 调试技巧](#常见问题--调试技巧)  
8. [扩展 & 定制](#扩展--定制)  

---

## 快速上手

```java
// 注入 CacheFacade（Spring/Quarkus 自动注入）
@Inject
CacheFacade cacheFacade;

// 读取缓存（返回 Optional）
Optional<User> optUser = cacheFacade.get("user:123", User.class);
optUser.ifPresent(user -> log.info("User from cache: {}", user));

// 缓存读取或加载（Cache Aside）
User user = cacheFacade.getOrLoad(
        "user:123",
        User.class,
        () -> userRepository.findById(123L),   // 数据库加载函数
        Duration.ofHours(1)                    // 自定义 TTL（可为 null 使用默认）
);

// 写入缓存（统一 API）
cacheFacade.put("user:123", user, Duration.ofHours(2));

// 删除单条缓存
cacheFacade.invalidate("user:123");

// 清空全部业务缓存（仅 admin:* 前缀的键）
cacheFacade.invalidateAll();
```

> **Tip**：业务层只需要提供业务键（如 `user:123`），`CacheFacade` 会自动拼接全局前缀 `admin:`，并根据配置的策略决定走本地、Redis 或二级缓存。

---

## 缓存结构概览

```
┌─────────────────────┐
│   CacheFacade (Facade)│
│  ─────────────────── │
│  • 统一入口 API      │
│  • 根据 CacheStrategy│
│    自动路由           │
└───────▲───────▲──────┘
        │       │
   L1 本地缓存   L2 Redis
   (Caffeine)   (Quarkus Redis)
        │       │
        ▼       ▼
   本地缓存命中  Redis 命中
        │       │
        └───────┘
        │
  布隆过滤器（Redis Bitmap） → 防止缓存穿透
```

- **L1 本地缓存**：`LocalCacheManager`（基于 Quarkus Cache / Caffeine），读写速度极快，适用于热点数据。  
- **L2 Redis 缓存**：`RedisCacheManager`，分布式共享，提供持久化与跨实例一致性。  
- **布隆过滤器**：`RedisBloomFilter`，在读取前快速判断键是否可能存在，降低 DB 访问压力。

---

## 核心 API（CacheFacade）

| 方法 | 说明 | 参数 | 返回 |
|------|------|------|------|
| `get(String key, Class<T> type)` | 读取缓存，依据策略自动路由 | 业务键、目标类型 | `Optional<T>` |
| `getOrLoad(String key, Class<T> type, Supplier<T> loader, Duration ttl)` | Cache‑Aside：缓存未命中时调用 `loader` 加载并写入缓存 | 业务键、目标类型、加载函数、TTL（可 null） | `T` |
| `put(String key, T value, Duration ttl)` | 写入缓存，支持空值占位（`value == null`） | 业务键、值、TTL（可 null） | `void` |
| `invalidate(String key)` | 删除单条缓存 | 业务键 | `void` |
| `invalidateAll()` | 清空所有业务缓存（仅 `admin:*` 前缀） | — | `void` |

> **空值占位**：当 `value == null` 时，内部会写入特殊占位符 `__NULL__` 并使用 `nullValueTtl`（默认 2 分钟），防止同键的缓存穿透。

---

## 缓存策略（CacheStrategy）

| 策略 | 说明 | 适用场景 |
|------|------|----------|
| `LOCAL_ONLY` | 仅使用本地 L1 缓存 | 数据更新极少、对一致性要求不高的热点数据 |
| `REDIS_ONLY` | 仅使用 Redis L2 缓存 | 多实例共享、需要强一致性 |
| `TWO_LEVEL` | 本地 → Redis 双层缓存（默认） | 读多写少的热点业务，兼顾性能与一致性 |
| `READ_WRITE_THROUGH` | 读取直接走 Redis，写入同步更新本地 | 需要强一致性且读写频繁的业务 |
| `WRITE_BEHIND` | 写入仅写本地，异步回写 Redis（当前实现为同步） | 写入压力大、可容忍短暂不一致的场景 |

> **切换策略**：只需在 `application.yaml` 中修改 `app.cache.default-strategy`，无需改动业务代码。

---

## 防护机制

### 1. 防穿透（空值占位）

- 当查询结果为 `null`，`CacheFacade.put` 会写入 `CacheConstants.NULL_PLACEHOLDER_VALUE`（`__NULL__`）并使用 `nullValueTtl`（默认 2 分钟）。  
- 读取时若命中占位符，直接返回 `Optional.empty()`，避免再次查询 DB。

### 2. 防雪崩（TTL 随机抖动）

- `CacheConfig.ttl-jitter-enabled` 开启后，`RedisCacheManager` 会在基础 TTL 上随机增加 `0~maxTtlJitter` 秒（默认 5 分钟），分散缓存失效时间。

### 3. 防击穿（布隆过滤 + 分布式锁）

- **布隆过滤**：`RedisBloomFilter` 基于 Redis Bitmap，`CacheFacade.getOrLoad` 在使用二级或 Redis 相关策略时先检查 `bloomFilter.mightContain(key, key)`。  
- **分布式锁**（预留）：在实际业务中，可在 `CacheFacade` 中加入 `LockProvider`（如 Redisson）实现 “只让第一个请求加载 DB”，后续请求等待或返回空值。

---

## 配置示例（`application.yaml`）

```yaml
app:
  cache:
    enabled: true
    default-strategy: TWO_LEVEL          # 读取策略，可改为 LOCAL_ONLY / REDIS_ONLY / READ_WRITE_THROUGH / WRITE_BEHIND
    default-ttl: PT1H                    # 默认 TTL 1 小时
    null-value-ttl: PT2M                 # 空值占位 TTL 2 分钟
    ttl-jitter-enabled: true
    max-ttl-jitter: PT5M                 # 最大抖动 5 分钟

    bloom-filter:
      enabled: true
      expected-insertions: 1000000
      false-positive-rate: 0.01

    local:
      enabled: true
      maximum-size: 20000                # 本地缓存最大条目数
      expire-after-write: PT10M          # 本地缓存默认过期时间

    redis:
      enabled: true
      key-prefix: "admin:"                # 所有 Redis 键统一前缀
      timeout: PT5S                      # Redis 命令超时
      compression-enabled: false
      compression-threshold: 1024
```

> **注意**：若 `app.cache.enabled` 为 `false`，所有缓存相关操作将直接透传到业务层（相当于关闭缓存）。

---

## 常见问题 & 调试技巧

| 场景 | 可能原因 | 解决方案 |
|------|----------|----------|
| **缓存总是 MISS** | 本地缓存未创建 / Redis 连接异常 | 检查 `CacheConfig.enabled`、`local.enabled`、`redis.enabled`；查看 Quarkus 启动日志中的 `Cache` 与 `Redis` 初始化信息 |
| **空值占位未生效** | `value == null` 时未走 `put` | 确认 `CacheFacade.put` 调用时 `value` 为 `null`，并检查 `nullValueTtl` 是否大于 0 |
| **TTL 抖动未生效** | `ttl-jitter-enabled` 为 `false` | 在 `application.yaml` 中打开 `ttl-jitter-enabled` 并设置 `max-ttl-jitter` |
| **布隆过滤误判率过高** | `expected-insertions` 与实际数据量差距大 | 调整 `expected-insertions` 与 `false-positive-rate`，重新生成过滤器（`RedisBloomFilter.addAll`） |
| **分布式锁未实现** | 需要防止热点键同时查询 DB | 在业务层使用 `@Lock`（Redisson）或自行在 `CacheFacade` 中加入 `LockProvider` 实现 `setIfAbsent` 逻辑 |

**日志调试**：`CacheFacade`、`LocalCacheManager`、`RedisCacheManager` 均使用 `CacheOperationType` 记录操作，开启 `DEBUG` 级别即可看到：

```properties
quarkus.log.category."io.github.faustofan.admin.shared".level=DEBUG
```

---

## 扩展 & 定制

1. **自定义序列化**  
   - `RedisCacheManager` 使用 Jackson `ObjectMapper`，如需自定义模块（如 `JavaTimeModule`），在 `application.yml` 中配置 `quarkus.jackson`，或在 `RedisCacheManager` 构造函数中注入自定义 `ObjectMapper`。

2. **自定义本地缓存名称**  
   - `CacheConstants.LocalCacheName` 已预定义常用缓存名称（`USER_CACHE`、`ROLE_CACHE` 等），如需新增，只需在 `CacheConstants` 中添加对应常量，并在业务层使用。

3. **分布式锁实现**  
   - 可在 `CacheFacade` 中引入 `io.quarkus.redis.datasource.lock.LockCommands`，实现 `setIfAbsent`（SET NX）+ `expire` 组合，确保同一键只会有一个线程加载 DB。

4. **监控 & Metrics**  
   - 通过 `CacheOperationType` 与 Quarkus Micrometer 集成，可在 `application.yaml` 中开启指标收集：

   ```yaml
   quarkus:
     micrometer:
       enabled: true
       export:
         prometheus:
           enabled: true
   ```
   - 在 `CacheFacade` 中使用 `@Counted`、`@Timed` 注解记录命中率、响应时间等。

---


