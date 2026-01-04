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

## 方法级缓存注解

为了简化业务开发，我们提供了一套声明式缓存注解，无需手动调用 `CacheFacade`，只需在方法上添加注解即可自动完成缓存操作。

### 注解概览

| 注解 | 说明 | 适用场景 |
|------|------|----------|
| `@Cacheable` | 先查缓存，命中则返回，未命中则执行方法并缓存结果 | 查询类方法 |
| `@CacheEvict` | 清除缓存 | 删除、更新操作 |
| `@CachePut` | 始终执行方法，并将结果放入缓存 | 保存、更新操作 |
| `@Caching` | 组合多个缓存操作 | 复杂场景（同时更新多个缓存） |

---

### @Cacheable 使用示例

```java
import io.github.faustofan.admin.shared.cache.annotation.Cacheable;
import io.github.faustofan.admin.shared.cache.constants.CacheStrategy;

@ApplicationScoped
public class UserService {

    @Inject
    UserRepository userRepository;

    // 基础用法：根据 ID 缓存用户
    @Cacheable(key = "'user:' + #id", ttl = "PT1H")
    public User findById(Long id) {
        return userRepository.findById(id);
    }

    // 使用命名空间和参数名
    @Cacheable(
        cacheName = "user",
        key = "#username",
        ttl = "PT30M"
    )
    public User findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    // 条件缓存：只有 ID > 0 时才缓存
    @Cacheable(
        key = "'user:' + #id",
        condition = "#id > 0",
        unless = "#result == null"
    )
    public User findByIdWithCondition(Long id) {
        return userRepository.findById(id);
    }

    // 启用分布式锁保护（防止缓存击穿）
    @Cacheable(
        key = "'hotspot:user:' + #id",
        lockProtection = true,
        ttl = "PT10M"
    )
    public User findHotspotUser(Long id) {
        return userRepository.findById(id);
    }

    // 使用对象属性作为 Key
    @Cacheable(key = "'user:' + #query.tenantId + ':' + #query.username")
    public User findByQuery(UserQuery query) {
        return userRepository.findByQuery(query);
    }
}
```

**注解属性说明：**

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `key` | String | 自动生成 | 缓存Key表达式，支持 SpEL |
| `cacheName` | String | "" | 缓存命名空间 |
| `ttl` | String | 配置默认值 | 过期时间（ISO-8601 格式） |
| `strategy` | CacheStrategy | TWO_LEVEL | 缓存策略 |
| `condition` | String | "" | 缓存条件表达式 |
| `unless` | String | "" | 结果排除条件 |
| `lockProtection` | boolean | false | 是否启用分布式锁 |
| `cacheNullValue` | boolean | true | 是否缓存空值 |

---

### @CacheEvict 使用示例

```java
import io.github.faustofan.admin.shared.cache.annotation.CacheEvict;

@ApplicationScoped
public class UserService {

    // 删除单个缓存
    @CacheEvict(key = "'user:' + #id")
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    // 更新时清除缓存
    @CacheEvict(key = "'user:' + #user.id")
    public User updateUser(User user) {
        return userRepository.save(user);
    }

    // 清除整个命名空间的缓存
    @CacheEvict(cacheName = "user", allEntries = true)
    public void clearAllUserCache() {
        // 批量操作...
    }

    // 方法执行前清除（用于特殊场景）
    @CacheEvict(
        key = "'order:' + #orderId",
        beforeInvocation = true
    )
    public void processOrder(Long orderId) {
        // 先清除缓存，再处理订单
    }

    // 条件清除
    @CacheEvict(
        key = "'user:' + #id",
        condition = "#force == true"
    )
    public void refreshUser(Long id, boolean force) {
        // ...
    }
}
```

**注解属性说明：**

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `key` | String | "" | 要清除的缓存Key |
| `cacheName` | String | "" | 缓存命名空间 |
| `allEntries` | boolean | false | 是否清除整个命名空间 |
| `beforeInvocation` | boolean | false | 是否在方法执行前清除 |
| `condition` | String | "" | 清除条件表达式 |

---

### @CachePut 使用示例

```java
import io.github.faustofan.admin.shared.cache.annotation.CachePut;

@ApplicationScoped
public class UserService {

    // 更新用户时同步更新缓存
    @CachePut(key = "'user:' + #user.id")
    public User updateUser(User user) {
        return userRepository.save(user);
    }

    // 创建用户时写入缓存（使用返回值的 ID）
    @CachePut(
        key = "'user:' + #result.id",
        condition = "#result != null"
    )
    public User createUser(UserRequest request) {
        User user = convertToUser(request);
        return userRepository.save(user);
    }

    // 不缓存 null 结果
    @CachePut(
        key = "'user:' + #id",
        cacheNullValue = false
    )
    public User refreshUser(Long id) {
        return userRepository.findById(id);
    }
}
```

---

### @Caching 组合使用示例

```java
import io.github.faustofan.admin.shared.cache.annotation.*;

@ApplicationScoped
public class UserService {

    // 更新用户时：更新主缓存，清除关联缓存
    @Caching(
        put = {
            @CachePut(key = "'user:' + #result.id")
        },
        evict = {
            @CacheEvict(key = "'user:username:' + #user.username"),
            @CacheEvict(key = "'user:email:' + #user.email")
        }
    )
    public User updateUser(User user) {
        return userRepository.save(user);
    }

    // 删除用户时清除多个相关缓存
    @Caching(evict = {
        @CacheEvict(key = "'user:' + #userId"),
        @CacheEvict(key = "'user:roles:' + #userId"),
        @CacheEvict(key = "'user:permissions:' + #userId")
    })
    public void deleteUser(Long userId) {
        userRepository.deleteById(userId);
    }

    // 批量查询：缓存多个 Key
    @Caching(cacheable = {
        @Cacheable(key = "'tenant:' + #tenantId + ':users'"),
        @Cacheable(cacheName = "user-list", key = "#tenantId")
    })
    public List<User> findByTenantId(Long tenantId) {
        return userRepository.findByTenantId(tenantId);
    }
}
```

---

### SpEL 表达式语法

缓存注解中的 `key`、`condition`、`unless` 属性支持简化版 SpEL 表达式：

| 表达式 | 说明 | 示例 |
|--------|------|------|
| `#paramName` | 方法参数名 | `#id`, `#username` |
| `#p0`, `#p1` | 参数索引 | `#p0`, `#p1` |
| `#result` | 方法返回值 | `#result.id` |
| `#param.property` | 参数属性 | `#user.id`, `#query.name` |
| 字符串拼接 | 使用 `+` 连接 | `'user:' + #id` |
| 条件判断 | 比较运算符 | `#id > 0`, `#name != null` |

**示例：**

```java
// 使用参数名
@Cacheable(key = "'user:' + #id")

// 使用参数索引
@Cacheable(key = "'user:' + #p0")

// 使用参数属性
@Cacheable(key = "'user:' + #request.userId")

// 使用返回值属性（仅 @CachePut 和 unless 中可用）
@CachePut(key = "'user:' + #result.id")

// 条件表达式
@Cacheable(key = "'user:' + #id", condition = "#id > 0")

// 排除 null 结果
@Cacheable(key = "'user:' + #id", unless = "#result == null")
```

---

### 注解 vs 编程式 API

| 场景 | 推荐方式 | 说明 |
|------|----------|------|
| 简单的 CRUD 缓存 | `@Cacheable` / `@CacheEvict` | 声明式，代码简洁 |
| 复杂的缓存逻辑 | `CacheFacade` | 完全控制缓存流程 |
| 需要动态 Key 生成 | `CacheFacade` | 运行时构建 Key |
| 批量缓存操作 | `CacheFacade` | 更高效的批量处理 |
| 事务性缓存更新 | `CacheFacade` | 与业务事务协调 |

> **最佳实践**：Service 层的标准 CRUD 使用注解，复杂业务逻辑使用 `CacheFacade`。

---

### 编译配置

为了让 SpEL 表达式能够正确解析参数名，需要在编译时保留参数名信息：

**Maven 配置：**

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <compilerArgs>
            <arg>-parameters</arg>
        </compilerArgs>
    </configuration>
</plugin>
```

---

