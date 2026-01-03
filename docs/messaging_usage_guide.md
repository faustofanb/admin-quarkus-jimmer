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


