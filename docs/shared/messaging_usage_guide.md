# 消息基础设施使用指南（中文）

## 目录
1. [概述](#概述)
2. [配置说明](#配置说明)
3. [核心概念](#核心概念)
4. [如何注入 `MessagingFacade`](#如何注入-messagingfacade)
5. [发布事件（同步）](#发布事件同步)
6. [发送消息（原始传输层）](#发送消息原始传输层)
7. [异步 / 响应式 API](#异步-响应式-api)
8. [订阅事件（Reactive Stream）](#订阅事件-reactive-stream)
9. [指定通道使用](#指定通道使用)
10. [自定义通道扩展](#自定义通道扩展)
11. [最佳实践与常见坑点](#最佳实践与常见坑点)
12. [FAQ](#faq)
13. [参考文档与链接](#参考文档与链接)

---

## 概述
本项目提供了一套 **统一的消息总线**，通过 `EventBus` 接口抽象了三类不同的消息通道：

- **LOCAL**：基于 Quarkus CDI 的本地事件，仅在同一 JVM 内传播，适用于业务内部的轻量级解耦。
- **PULSAR**：分布式消息队列（需要 `quarkus-messaging-pulsar` 依赖），适合跨服务、跨节点的可靠投递。
- **STREAM**：基于 Mutiny 的响应式流实现，支持背压、批处理、流式聚合等高级特性。

所有通道实现统一的 `EventBus` 接口，外部只需要通过 **`MessagingFacade`** 进行调用，即可在配置层面自由切换底层实现，彻底避免了魔法字符串和硬编码。

---

## 配置说明
在 `application.yml`（或 `application.properties`）中加入以下配置块即可完成初始化。示例采用 **YAML**，如果使用 `properties`，请自行转换键名。

```yaml
admin:
  messaging:
    # 总开关，关闭后所有通道均不可用
    enabled: true
    # 默认使用的通道（LOCAL / PULSAR / STREAM）
    default-channel: LOCAL
    # 统一的投递模式（可选）
    delivery-mode: FIRE_AND_FORGET

    # ---------- 本地通道配置 ----------
    local:
      enabled: true          # 是否启用本地 CDI 事件
      async: true            # 是否使用 fireAsync 进行异步发布
      async-timeout: 5s      # 异步发布的超时时间（仅在需要时使用）

    # ---------- Pulsar 通道配置 ----------
    pulsar:
      enabled: false         # 当有 Pulsar 集群时改为 true
      service-url: pulsar://localhost:6650
      tenant: public
      namespace: default
      subscription-prefix: admin-
      send-timeout: 10s
      batching-enabled: true
      batch-max-bytes: 131072
      batch-max-messages: 1000
      batch-max-delay: 10ms

    # ---------- Stream 通道配置 ----------
    stream:
      enabled: true
      buffer-size: 128               # Mutiny 背压缓冲区大小
      backpressure-strategy: DROP   # DROP / LATEST / ERROR 等
```
> **注意**：只有在对应 `enabled` 为 `true` 时，相关实现才会被 CDI 注入。`default-channel` 决定 `MessagingFacade` 在不显式指定通道时使用哪一个实现。

---

## 核心概念
| 概念 | 说明 |
|------|------|
| **Event** | 业务事件的抽象，继承自 `io.github.faustofan.admin.shared.messaging.core.Event<T>`，包含 `eventId、eventType、source、topic、payload、metadata` 等字段。 |
| **Message** | 对应底层传输层的包装，包含 `messageId、topic、key、headers、payload`，可直接使用 `Message.of(topic, payload)` 快速创建。 |
| **ChannelType** | 枚举值 `LOCAL、PULSAR、STREAM`，用于在 `MessagingFacade` 中选择具体实现。 |
| **DeliveryMode** | 投递语义枚举：`FIRE_AND_FORGET、AT_LEAST_ONCE、AT_MOST_ONCE、EXACTLY_ONCE、SYNC`，可在配置中统一设置。 |
| **EventHandler** | 事件处理函数式接口，支持同步、异步（`CompletionStage`）以及 Mutiny `Uni` 三种方式。 |
| **MessagingFacade** | 统一门面，内部根据 `MessagingConfig` 自动路由到对应的 `EventBus` 实例。 |

---

## 如何注入 `MessagingFacade`
```java
import jakarta.inject.Inject;
import io.github.faustofan.admin.shared.messaging.facade.MessagingFacade;
import org.jboss.logging.Logger;

public class UserService {
    private static final Logger LOG = Logger.getLogger(UserService.class);

    @Inject
    MessagingFacade messagingFacade; // CDI 自动注入

    // 业务方法中直接使用
    public void createUser(UserDto dto) {
        // ...业务逻辑
        // 触发事件
        DomainEvent event = DomainEvent.builder()
                .eventId("user-" + dto.getId())
                .eventType(io.github.faustofan.admin.shared.messaging.constants.EventType.CREATED)
                .source("UserService")
                .topic("admin.system.user")
                .payload(dto)
                .build();
        messagingFacade.publish(event);
    }
}
```
> **Tip**：`MessagingFacade` 为 `@ApplicationScoped`，在任何 CDI 环境（如 Quarkus 控制器、服务、监听器）中均可直接注入。

---

## 发布事件（同步）
```java
DomainEvent event = DomainEvent.builder()
        .eventId("order-20230101")
        .eventType(EventType.CREATED)
        .source("OrderService")
        .topic("admin.system.order")
        .payload(orderDto)
        .build();

messagingFacade.publish(event); // 使用默认通道（由配置决定）
```
- **同步**：方法在内部会根据通道特性决定是否阻塞。例如 `LOCAL` 会立即调用 CDI `fire`，`PULSAR` 会阻塞等待发送完成（除非使用 `FIRE_AND_FORGET`）。
- **指定主题**：如果需要覆盖事件自带的 `topic`，可以使用 `publish(String topic, Event<T> event)` 方法。

---

## 发送消息（原始传输层）
当你需要操作底层的 **Message**（例如自定义 `key`、`headers`）时，使用 `send` 系列方法。
```java
Message<UserDto> msg = Message.builder()
        .topic("admin.system.user")
        .key("user-" + userDto.getId())
        .header("trace-id", "abc123")
        .payload(userDto)
        .build();

messagingFacade.send(msg);
```
- `send` 与 `publish` 的区别在于：`publish` 只关注业务事件语义，`send` 允许你直接控制消息属性（适用于 Pulsar、Kafka 等底层实现）。

---

## 异步 / 响应式 API
### CompletionStage（Java 8+）
```java
messagingFacade.publishAsync(event)
        .thenRun(() -> LOG.info("异步发布完成"))
        .exceptionally(ex -> { LOG.error("发布失败", ex); return null; });
```
### Mutiny Uni（推荐）
```java
messagingFacade.publishUni(event)
        .subscribe().with(
            ignored -> LOG.info("Uni 发布成功"),
            err -> LOG.error("Uni 发布异常", err)
        );
```
> **注意**：异步 API 会保持底层通道的投递语义。例如 Pulsar 在 `AT_LEAST_ONCE` 模式下，`publishAsync` 仍然会在失败时返回异常。

---

## 订阅事件（Reactive Stream）
```java
import io.smallrye.mutiny.Multi;

Multi<DomainEvent> stream = messagingFacade.subscribe("admin.system.user", DomainEvent.class);
stream
    .filter(ev -> ev.getEventType() == EventType.CREATED)
    .onItem().transformToUni(ev -> processUserCreated(ev)) // 业务处理返回 Uni
    .subscribe().with(
        result -> LOG.info("处理完成: " + result),
        err -> LOG.error("流处理异常", err)
    );
```
- `subscribe` 返回 **Mutiny `Multi`**，天然支持背压、分批、合并等操作。
- 若需要直接获取 **Message**（包括自定义 header），使用 `subscribeMessages`。
- **StreamEventBus** 还提供了 `subscribeUnicast`（单播）以及 `merge`、`pipe`、`subscribeBatch` 等高级 API，详见 `StreamEventBus` 类注释。

---

## 指定通道使用
默认情况下 `MessagingFacade` 会使用 `admin.messaging.default-channel` 配置的通道。如果业务需要显式指定通道（例如同一个服务同时向本地和 Pulsar 发送），可以这样做：
```java
// 获取 Pulsar 实例
EventBus pulsarBus = messagingFacade.getEventBus(ChannelType.PULSAR);

pulsarBus.publish(event); // 直接使用 Pulsar
```
- 若对应通道未启用，`getEventBus` 会抛出 `MessagingException.channelUnavailable`，请做好异常捕获。

---

## 自定义通道扩展
1. **实现 `EventBus` 接口**：提供所有 `publish、send、fire、subscribe` 等方法的具体实现。
2. **注册为 CDI Bean**：在实现类上添加 `@ApplicationScoped`（或其他作用域），Quarkus 会自动发现。
3. **扩展 `ChannelType` 枚举**：在 `io.github.faustofan.admin.shared.messaging.constants.ChannelType` 中新增枚举值，例如 `KAFKA`。
4. **在 `MessagingConfig` 中添加配置块**：为新通道提供专属配置属性。
5. **更新 `MessagingFacade.getEventBus`**：加入新枚举的分支逻辑。
6. **编写单元测试**：确保新实现符合 `EventBus` 合约。

> **最佳实践**：保持 `EventBus` 实现的 **无状态**（除非必须），并使用 `CompletionStage`/`Uni` 统一返回异步结果，便于上层统一处理。

---

## 最佳实践与常见坑点
- **统一使用 Facade**：直接使用 `MessagingFacade` 能够让代码在切换底层实现时无需改动。
- **避免在业务代码中硬编码 Topic**：使用 `MessagingConstants` 中统一定义的前缀或枚举，保持一致性。
- **事件对象应保持不可变**：`BaseEvent` 已提供 builder，尽量在创建后不再修改属性。
- **合理配置背压**：在高并发场景下，`STREAM` 通道的 `buffer-size` 与 `backpressure-strategy` 必须根据业务峰值进行调优，防止 OOM。
- **异常处理**：所有 `publish`/`send` 方法在底层异常时会抛出 `MessagingException`，业务层应捕获并做重试或告警。
- **关闭资源**：如果手动管理 `StreamEventBus`（如在测试中），记得在应用停止时调用 `closeAllStreams()`，防止线程泄漏。
- **Pulsar 依赖**：在使用 Pulsar 前，请确保 `quarkus-messaging-pulsar` 已加入 `pom.xml`，并在 `application.yml` 中正确配置 `service-url`、`tenant`、`namespace`。

---

## FAQ
| 问题 | 解答 |
|------|------|
| **如果 Pulsar 未启用，我仍然调用 `messagingFacade.getEventBus(ChannelType.PULSAR)` 会怎样？** | 会抛出 `MessagingException.channelUnavailable`，请捕获或在配置中打开对应通道。 |
| **可以在同一个服务中同时使用多个通道吗？** | 完全可以，直接通过 `getEventBus` 获取不同实现并分别调用。 |
| **本地事件是否支持跨进程？** | 不支持，LOCAL 只在同一 JVM 内传播。如果需要跨进程，请使用 Pulsar 或 Stream。 |
| **如何给 Message 添加自定义 Header？** | 使用 `Message.builder().header("key", "value").build()`，在 Pulsar/Stream 中会原样传递。 |
| **在测试环境如何关闭 Stream 的后台线程？** | 调用 `messagingFacade.getEventBus(ChannelType.STREAM).closeAllStreams()`（需要强转为 `StreamEventBus`），或在测试结束时使用 `@AfterAll` 注解执行。 |

---

## 参考文档与链接
- **代码入口**：`io.github.faustofan.admin.shared.messaging.facade.MessagingFacade`
- **核心接口**：`io.github.faustofan.admin.shared.messaging.core.EventBus`
- **配置接口**：`io.github.faustofan.admin.shared.messaging.config.MessagingConfig`
- **常量枚举**：`io.github.faustofan.admin.shared.messaging.constants.*`
- **本地实现**：`io.github.faustofan.admin.shared.messaging.local.LocalEventBus`
- **Pulsar 实现**：`io.github.faustofan.admin.shared.messaging.pulsar.PulsarEventBus`
- **Stream 实现**：`io.github.faustofan.admin.shared.messaging.stream.StreamEventBus`
- **Quarkus 文档**：https://quarkus.io/guides/cdi-reference
- **Mutiny 文档**：https://smallrye.io/smallrye-mutiny/
- **Apache Pulsar 官方文档**：https://pulsar.apache.org/docs/

---

## 声明式注解

为了简化业务开发，我们提供了一套声明式注解，无需手动调用 `MessagingFacade`，只需在方法上添加注解即可自动完成事件发布和订阅。

### 注解概览

| 注解 | 说明 | 适用场景 |
|------|------|----------|
| `@EventListener` | 事件监听，自动订阅并处理事件 | 事件消费端 |
| `@EventPublish` | 事件发布，方法执行后自动发布事件 | 事件生产端 |

---

### @EventListener 事件监听注解

```java
import io.github.faustofan.admin.shared.messaging.annotation.EventListener;
import io.github.faustofan.admin.shared.messaging.constants.EventType;
import io.github.faustofan.admin.shared.messaging.constants.ChannelType;
import io.github.faustofan.admin.shared.messaging.constants.MessagingConstants;
import io.github.faustofan.admin.shared.messaging.core.DomainEvent;

@ApplicationScoped
public class UserEventHandler {

    // 监听用户创建事件
    @EventListener(
        topic = MessagingConstants.SystemTopic.USER_EVENTS,
        eventType = EventType.CREATED
    )
    public void onUserCreated(DomainEvent<UserDto> event) {
        log.info("User created: " + event.getPayload().getUsername());
        // 发送欢迎邮件、初始化用户数据等
    }

    // 监听所有用户事件
    @EventListener(topic = "admin.system.user")
    public void onUserEvent(DomainEvent<UserDto> event) {
        log.info("User event: " + event.getEventType());
    }

    // 指定使用 Pulsar 通道
    @EventListener(
        topic = "admin.integration.order",
        channel = ChannelType.PULSAR,
        consumerGroup = "order-handler-group"
    )
    public void onOrderEvent(IntegrationEvent<OrderDto> event) {
        orderService.syncOrder(event.getPayload());
    }

    // 异步处理 + 失败重试
    @EventListener(
        topic = MessagingConstants.BusinessTopic.PAYMENT_EVENTS,
        eventType = EventType.CREATED,
        async = true,
        retryCount = 3,
        retryInterval = "PT5S"
    )
    public void onPaymentCreated(DomainEvent<PaymentDto> event) {
        notificationService.sendPaymentNotification(event.getPayload());
    }

    // 优先级控制
    @EventListener(
        topic = "admin.system.config",
        eventType = EventType.CONFIG_CHANGED,
        priority = 10,  // 数值越小优先级越高
        description = "配置变更处理器"
    )
    public void onConfigChanged(DomainEvent<ConfigDto> event) {
        cacheManager.refresh();
    }
}
```

**注解属性说明：**

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `topic` | String | **必填** | 订阅的 Topic |
| `eventType` | EventType[] | 全部 | 事件类型过滤 |
| `channel` | ChannelType | AUTO | 消息通道类型 |
| `consumerGroup` | String | "" | 消费者组名称 |
| `async` | boolean | false | 是否异步处理 |
| `retryCount` | int | 0 | 失败重试次数 |
| `retryInterval` | String | "PT1S" | 重试间隔 |
| `condition` | String | "" | 处理条件表达式 |
| `priority` | int | 100 | 优先级（数值越小越高） |
| `description` | String | "" | 描述信息 |

---

### @EventPublish 事件发布注解

```java
import io.github.faustofan.admin.shared.messaging.annotation.EventPublish;
import io.github.faustofan.admin.shared.messaging.constants.DeliveryMode;

@ApplicationScoped
public class UserService {

    // 方法执行后自动发布事件（返回值作为 payload）
    @EventPublish(
        topic = "admin.system.user",
        eventType = "created"
    )
    public User createUser(UserRequest request) {
        return userRepository.save(convertToUser(request));
    }

    // 使用 SpEL 表达式指定 payload
    @EventPublish(
        topic = "admin.system.user",
        eventType = "updated",
        payload = "#result"
    )
    public User updateUser(Long userId, UserRequest request) {
        return userRepository.update(userId, request);
    }

    // 条件发布
    @EventPublish(
        topic = "admin.system.user",
        eventType = "deleted",
        condition = "#result == true"
    )
    public boolean deleteUser(Long userId) {
        return userRepository.deleteById(userId);
    }

    // 异步发布
    @EventPublish(
        topic = "admin.business.order",
        eventType = "created",
        async = true,
        deliveryMode = DeliveryMode.AT_LEAST_ONCE
    )
    public Order createOrder(OrderRequest request) {
        return orderRepository.save(request);
    }

    // 自定义事件来源
    @EventPublish(
        topic = "admin.audit.operation",
        eventType = "logged",
        source = "AuditService",
        throwOnFailure = false  // 发布失败不影响业务
    )
    public void logOperation(OperationLog log) {
        // 业务逻辑
    }
}
```

**注解属性说明：**

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `topic` | String | **必填** | 发布的 Topic |
| `eventType` | String | "custom" | 事件类型 |
| `channel` | ChannelType | AUTO | 消息通道类型 |
| `deliveryMode` | DeliveryMode | FIRE_AND_FORGET | 投递模式 |
| `payload` | String | 返回值 | Payload 表达式 |
| `source` | String | 类名.方法名 | 事件来源 |
| `async` | boolean | false | 是否异步发布 |
| `condition` | String | "" | 发布条件表达式 |
| `beforeInvocation` | boolean | false | 是否在方法前发布 |
| `throwOnFailure` | boolean | false | 发布失败是否抛异常 |

---

### 注解 vs 编程式 API

| 场景 | 推荐方式 | 说明 |
|------|----------|------|
| 简单的事件监听 | `@EventListener` | 声明式，代码简洁 |
| 方法执行后发布事件 | `@EventPublish` | 与业务逻辑解耦 |
| 复杂的事件流处理 | `MessagingFacade.subscribe()` | 使用 Mutiny Multi |
| 需要控制投递确认 | `MessagingFacade` | 完全控制发布流程 |
| 批量事件处理 | `StreamEventBus.subscribeBatch()` | 高级流处理 |

> **最佳实践**：事件消费使用 `@EventListener`，简单发布使用 `@EventPublish`，复杂场景使用 `MessagingFacade`。

---

### 组合使用示例

```java
@ApplicationScoped
public class OrderService {

    /**
     * 订单创建：创建订单后发布事件，触发下游处理
     */
    @EventPublish(
        topic = "admin.business.order",
        eventType = "created"
    )
    public Order createOrder(OrderRequest request) {
        Order order = orderRepository.save(convertToOrder(request));
        return order;
    }
}

@ApplicationScoped
public class OrderEventHandler {

    /**
     * 监听订单创建事件：发送通知
     */
    @EventListener(
        topic = "admin.business.order",
        eventType = EventType.CREATED,
        priority = 10
    )
    public void onOrderCreatedForNotification(DomainEvent<Order> event) {
        notificationService.sendOrderConfirmation(event.getPayload());
    }

    /**
     * 监听订单创建事件：更新库存
     */
    @EventListener(
        topic = "admin.business.order",
        eventType = EventType.CREATED,
        priority = 20
    )
    public void onOrderCreatedForInventory(DomainEvent<Order> event) {
        inventoryService.reserveStock(event.getPayload());
    }

    /**
     * 监听订单创建事件：记录审计日志
     */
    @EventListener(
        topic = "admin.business.order",
        eventType = EventType.CREATED,
        async = true,  // 异步执行不阻塞主流程
        priority = 100
    )
    public void onOrderCreatedForAudit(DomainEvent<Order> event) {
        auditService.logOrderCreation(event.getPayload());
    }
}
```

---

## 实体生命周期事件发布（Jimmer 触发器）

本项目集成了 **Jimmer Transaction Trigger**，所有继承自 `AuditEntity` 的实体在执行 INSERT/UPDATE/DELETE 操作时，会自动发布相应的领域事件到消息总线。

### 工作原理

```
┌─────────────────┐      ┌───────────────────────┐      ┌─────────────────┐
│  Service 层     │      │  Jimmer SqlClient     │      │  Database       │
│  save/update/   │─────>│  Transaction Trigger  │─────>│  INSERT/UPDATE  │
│  delete         │      │  EntityEvent 触发     │      │  DELETE         │
└─────────────────┘      └───────────────────────┘      └─────────────────┘
                                   │
                                   ▼
                         ┌───────────────────────┐
                         │  AuditEntityEvent     │
                         │  Listener             │
                         │  转换为 EntityChange  │
                         │  Event                │
                         └───────────────────────┘
                                   │
                                   ▼
                         ┌───────────────────────┐
                         │  EntityEventPublisher │
                         │  发布 DomainEvent     │
                         │  到 MessagingFacade   │
                         └───────────────────────┘
                                   │
                                   ▼
                         ┌───────────────────────┐
                         │  @EventListener       │
                         │  监听并处理事件       │
                         └───────────────────────┘
```

### 配置启用

确保在 `application.yaml` 中启用了 Jimmer 触发器：

```yaml
quarkus:
  jimmer:
    active: true
    # 启用事务触发器，用于实体生命周期事件发布
    trigger-type: TRANSACTION_ONLY
```

> **触发器类型说明**：
> - `TRANSACTION_ONLY`：在事务内同步触发，事件会在 SQL 执行后立即发布
> - `BINLOG_ONLY`：基于数据库 BinLog 触发，需要额外配置
> - `BOTH`：同时支持两种模式

### Topic 命名规则

实体事件的 Topic 格式为：`admin.domain.{entitySimpleName.toLowerCase()}`

| 实体类 | Topic |
|--------|-------|
| `SystemUser` | `admin.domain.systemuser` |
| `SystemRole` | `admin.domain.systemrole` |
| `SystemMenu` | `admin.domain.systemmenu` |
| `SystemDept` | `admin.domain.systemdept` |
| `SystemPost` | `admin.domain.systempost` |
| `SystemTenant` | `admin.domain.systemtenant` |

可以使用 `MessagingConstants.EntityTopic` 中预定义的常量：

```java
import io.github.faustofan.admin.shared.messaging.constants.MessagingConstants;

// 使用常量而非硬编码字符串
String topic = MessagingConstants.EntityTopic.SYSTEM_USER;  // admin.domain.systemuser

// 动态生成 Topic
String dynamicTopic = MessagingConstants.EntityTopic.forEntity(SystemRole.class);  // admin.domain.systemrole
```

### 监听实体事件

#### 监听特定实体的特定操作

```java
import io.github.faustofan.admin.shared.messaging.annotation.EventListener;
import io.github.faustofan.admin.shared.messaging.constants.EventType;
import io.github.faustofan.admin.shared.messaging.constants.MessagingConstants;
import io.github.faustofan.admin.shared.messaging.core.DomainEvent;
import io.github.faustofan.admin.shared.persistence.event.EntityChangeEvent;

@ApplicationScoped
public class UserEventHandler {

    /**
     * 监听用户创建事件
     */
    @EventListener(
        topic = MessagingConstants.EntityTopic.SYSTEM_USER,
        eventType = EventType.CREATED,
        description = "用户创建事件处理"
    )
    public void onUserCreated(DomainEvent<EntityChangeEvent<?>> event) {
        EntityChangeEvent<?> changeEvent = event.getPayload();
        
        // 获取实体 ID
        Object userId = changeEvent.getEntityId();
        
        // 获取新创建的实体
        Object newUser = changeEvent.getNewEntity();
        
        LOG.infov("User created: id={0}", userId);
        
        // 业务逻辑：发送欢迎邮件、初始化用户配置等
    }

    /**
     * 监听用户更新事件
     */
    @EventListener(
        topic = MessagingConstants.EntityTopic.SYSTEM_USER,
        eventType = EventType.UPDATED
    )
    public void onUserUpdated(DomainEvent<EntityChangeEvent<?>> event) {
        EntityChangeEvent<?> changeEvent = event.getPayload();
        
        // 获取变更前后的实体
        Object oldUser = changeEvent.getOldEntity();
        Object newUser = changeEvent.getNewEntity();
        
        // 业务逻辑：同步到其他系统、更新缓存等
    }

    /**
     * 监听用户删除事件
     */
    @EventListener(
        topic = MessagingConstants.EntityTopic.SYSTEM_USER,
        eventType = EventType.DELETED
    )
    public void onUserDeleted(DomainEvent<EntityChangeEvent<?>> event) {
        EntityChangeEvent<?> changeEvent = event.getPayload();
        
        // 获取被删除的实体
        Object deletedUser = changeEvent.getOldEntity();
        
        // 业务逻辑：清理关联数据、发送通知等
    }
}
```

#### 监听所有变更事件

```java
/**
 * 监听角色的所有变更（创建、更新、删除）
 */
@EventListener(
    topic = MessagingConstants.EntityTopic.SYSTEM_ROLE,
    description = "角色变更事件处理"
)
public void onRoleChanged(DomainEvent<EntityChangeEvent<?>> event) {
    EntityChangeEvent<?> changeEvent = event.getPayload();
    
    switch (changeEvent.getEventType()) {
        case INSERTED -> handleRoleCreated(changeEvent);
        case UPDATED -> handleRoleUpdated(changeEvent);
        case DELETED -> handleRoleDeleted(changeEvent);
    }
    
    // 刷新权限缓存
    permissionCacheService.refresh();
}
```

### EntityChangeEvent 结构

```java
public class EntityChangeEvent<E> {
    
    private String eventId;           // 事件唯一标识
    private Instant occurredAt;       // 事件发生时间
    private EntityEventType eventType; // INSERTED / UPDATED / DELETED
    private String entityType;        // 实体类全限定名
    private Class<E> entityClass;     // 实体类
    private Object entityId;          // 实体 ID
    private E oldEntity;              // 变更前实体（INSERT 时为 null）
    private E newEntity;              // 变更后实体（DELETE 时为 null）
    private String source;            // 事件来源
    private Map<String, String> metadata;  // 扩展元数据
    
    // 便捷方法
    public boolean isInsert();        // 是否为插入事件
    public boolean isUpdate();        // 是否为更新事件
    public boolean isDelete();        // 是否为删除事件
    public E getEntity();             // 获取有效实体（新实体或旧实体）
}
```

### 典型使用场景

| 场景 | 事件类型 | 处理逻辑示例 |
|------|----------|--------------|
| 用户创建后发送欢迎邮件 | `CREATED` | 发送邮件、创建默认配置 |
| 用户信息变更同步到其他系统 | `UPDATED` | 调用外部 API、更新缓存 |
| 用户删除时清理关联数据 | `DELETED` | 删除日志、清理权限 |
| 角色权限变更刷新缓存 | `CREATED/UPDATED/DELETED` | 刷新权限缓存 |
| 菜单变更通知前端刷新 | `CREATED/UPDATED/DELETED` | WebSocket 推送 |
| 数据审计日志记录 | 所有 | 记录变更历史 |

### 注意事项

1. **只处理 AuditEntity 子类**：只有继承自 `AuditEntity` 的实体才会触发事件发布。
2. **事务内触发**：事件在事务内发布，如果事务回滚，事件监听器可能已经执行。对于关键操作，建议使用异步处理或在事务提交后确认。
3. **避免循环依赖**：事件监听器中如果再次修改同一个实体，可能导致无限循环。
4. **性能考虑**：同步事件处理会影响主业务性能，建议使用 `async = true` 进行异步处理。
5. **异常处理**：事件发布失败不会影响主业务流程，错误会被记录但不会抛出。

---

## 相关代码位置

| 组件 | 路径 |
|------|------|
| 实体事件类型 | `io.github.faustofan.admin.shared.persistence.event.EntityEventType` |
| 实体变更事件 | `io.github.faustofan.admin.shared.persistence.event.EntityChangeEvent` |
| 事件发布器 | `io.github.faustofan.admin.shared.persistence.event.EntityEventPublisher` |
| Jimmer 触发器配置 | `io.github.faustofan.admin.shared.persistence.trigger.JimmerTriggerConfiguration` |
| 审计实体监听器 | `io.github.faustofan.admin.shared.persistence.trigger.AuditEntityEventListener` |
| 示例监听器 | `io.github.faustofan.admin.shared.persistence.event.EntityChangeEventListener` |
| Topic 常量 | `io.github.faustofan.admin.shared.messaging.constants.MessagingConstants.EntityTopic` |

---
