# 异步使用指南 📚

> 本指南面向 **Quarkus** 项目中的开发者，帮助你快速、正确地使用我们新实现的 **异步基础设施**（基于 JDK 21 虚拟线程、MDC 与强类型 `AppContext` 透传）。

---

## 目录

1. [概览](#概览)
2. [核心概念](#核心概念)
3. [环境准备](#环境准备)
4. [上下文捕获与恢复](#上下文捕获与恢复)
5. [使用 `AsyncExecutor`](#使用-asyncexecutor)
6. [使用 `AsyncResult`](#使用-asyncresult)
7. [调度任务（`VirtualThreadScheduler`）](#调度任务)
8. [日志与 MDC 传播](#日志与-mdc-传播)
9. [常见错误与调试技巧](#常见错误与调试技巧)
10. [完整示例](#完整示例)

---

## 概览

我们提供了一套 **统一的异步 API**，包括：

- `AsyncExecutor`：最常用的入口，封装了 `CompletableFuture` 与虚拟线程池。
- `AsyncResult`：流式、链式的异步结果处理器，支持超时、回调、错误转换等。
- `VirtualThreadScheduler`：基于 JDK 21 虚拟线程的调度器，支持一次性延迟、周期任务。
- `AppContextHolder` 与 `AsyncContext`：负责在跨线程之间安全传递 **MDC**（日志追踪）以及 **业务上下文**（用户、租户、请求信息等）。

所有 API 都是 **无副作用** 的静态工具类，使用时不需要自行管理线程池或 `ThreadLocal`，只要在入口（如过滤器、拦截器）里把 `AppContext` 放入 `AppContextHolder` 即可。

---

## 核心概念

| 类/接口 | 作用 | 关键方法 |
|---|---|---|
| `AppContext` | 业务上下文的强类型模型（用户 ID、租户 ID、请求 ID、IP 等） | `builder()`, `getUserId()`, `isAuthenticated()` |
| `AppContextHolder` | `ThreadLocal` 持有 `AppContext` 与 MDC，提供捕获/恢复/清理 | `capture()`, `restore(AsyncContext)`, `clear()`, `setAppContext(AppContext)` |
| `AsyncContext` | 包装了 MDC Map 与 `AppContext`，实现跨线程快照 | `of(Map<String,String>, AppContext)`, `isEmpty()` |
| `AsyncExecutor` | 统一的异步入口，内部使用 `VirtualThreadExecutorFactory` | `runAsync(Runnable)`, `supplyAsync(Supplier)`, `callAsync(Callable)`, `schedule(...)` |
| `AsyncResult<T>` | 流式 API，包装 `CompletableFuture<T>`，提供 `onSuccess`, `onFailure`, `timeout` 等 | `of(Supplier)`, `map`, `flatMap`, `await` |
| `VirtualThreadScheduler` | 基于虚拟线程的调度器，支持一次性延迟和周期任务 | `schedule(Runnable, Duration)`, `schedule(Callable<V>, Duration)` |

---

## 环境准备

1. **JDK 21**（已在 `application.yaml` 中开启 `quarkus.virtual-threads.enabled: true`）
2. **MDC** 依赖：`org.slf4j:slf4j-api` 与 `ch.qos.logback:logback-classic`
3. **Quarkus** 依赖已包含 `quarkus-vertx`，无需额外配置。
4. **确保 `AppContextHolder` 已在请求入口处设置**（示例见下文）。

---

## 上下文捕获与恢复

### 1️⃣ 在请求过滤器/拦截器中构建 `AppContext`
```java
@Provider
@Priority(Priorities.AUTHENTICATION)
public class AppContextFilter implements ContainerRequestFilter {
    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        // 从 JWT、Header、Session 等获取业务信息
        Long userId = ...;
        String username = ...;
        Long tenantId = ...;
        String requestId = UUID.randomUUID().toString();
        String clientIp = requestContext.getHeaderString("X-Forwarded-For");

        AppContext appContext = AppContext.builder()
                .userId(userId)
                .username(username)
                .tenantId(tenantId)
                .requestId(requestId)
                .clientIp(clientIp)
                .build();

        // 放入 ThreadLocal，后续所有异步任务都能自动获取
        AppContextHolder.setAppContext(appContext);
        // 同步关键信息到 MDC，便于日志追踪
        AppContextHolder.syncAppContextToMdc();
    }
}
```
> **注意**：过滤器只在 HTTP 请求线程里执行，`AppContextHolder` 会在后续的虚拟线程中通过 `AsyncExecutor` 自动恢复。

### 2️⃣ 手动捕获（如在非 HTTP 场景）
```java
AsyncContext ctx = AppContextHolder.capture();
// 传递给子线程或任务
new Thread(() -> {
    AppContextHolder.restore(ctx);
    // ...业务代码
    AppContextHolder.clear();
}).start();
```

---

## 使用 `AsyncExecutor`

### 基本异步执行
```java
AsyncExecutor.runAsync(() -> {
    // 这里可以直接使用 AppContextHolder.getUserId() 等
    log.info("Current user: {}", AppContextHolder.getUserId().orElse(null));
});
```

### 有返回值的任务
```java
CompletableFuture<UserDto> future = AsyncExecutor.supplyAsync(() -> userService.findById(42L));
future.thenAccept(dto -> log.info("User fetched: {}", dto));
```

### Callable（可抛异常）
```java
AsyncExecutor.callAsync(() -> {
    if (!AppContextHolder.isAuthenticated()) {
        throw new IllegalStateException("未登录");
    }
    return orderService.createOrder(...);
}).whenComplete((order, ex) -> {
    if (ex != null) {
        log.error("创建订单失败", ex);
    } else {
        log.info("订单创建成功: {}", order.getId());
    }
});
```

### 延迟/周期任务（使用 `VirtualThreadScheduler`）
```java
// 5 秒后执行一次
VirtualThreadScheduler.schedule(() -> {
    log.info("Delayed task executed, userId={}", AppContextHolder.getUserId().orElse(null));
}, Duration.ofSeconds(5));

// 每分钟执行一次（返回 ScheduledFuture，可用于取消）
ScheduledFuture<?> heartbeat = VirtualThreadScheduler.scheduleAtFixedRate(() -> {
    log.info("Heartbeat, tenantId={}", AppContextHolder.getTenantId().orElse(null));
}, Duration.ZERO, Duration.ofMinutes(1));
```

---

## 使用 `AsyncResult`

`AsyncResult` 为 **流式** 异步编程提供了更友好的 API。

### 创建并链式处理
```java
AsyncResult.of(() -> userRepository.findById(1L))
    .map(user -> user.toDto())
    .onSuccess(dto -> log.info("User DTO: {}", dto))
    .onFailure(ex -> log.error("查询用户失败", ex))
    .timeout(Duration.ofSeconds(10))
    .await(); // 阻塞等待（仅在测试/脚本中使用）
```

### 组合多个异步结果
```java
AsyncResult<User> userResult = AsyncResult.of(() -> userService.getCurrentUser());
AsyncResult<List<Order>> ordersResult = AsyncResult.of(() -> orderService.listByUser(userResult.get()));

userResult.flatMap(user -> ordersResult.map(orders -> new UserOrdersDto(user, orders)))
          .onSuccess(dto -> log.info("Combined result: {}", dto))
          .onFailure(Throwable::printStackTrace);
```

---

## 调度任务（`VirtualThreadScheduler`）

| 方法 | 描述 |
|---|---|
| `schedule(Runnable, Duration)` | 延迟一次性执行 |
| `schedule(Callable<V>, Duration)` | 延迟一次性执行并返回结果 |
| `scheduleAtFixedRate(Runnable, Duration initialDelay, Duration period)` | 固定频率周期任务 |
| `scheduleWithFixedDelay(Runnable, Duration initialDelay, Duration delay)` | 固定间隔周期任务 |

所有调度方法内部都会 **捕获当前 `AsyncContext`**，并在任务执行时恢复，确保 MDC 与业务上下文完整。

---

## 日志与 MDC 传播

### MDC 键定义（`AsyncConstants.MdcKeys`）
- `TRACE_ID` – 链路追踪 ID
- `USER_ID` – 当前用户 ID
- `TENANT_ID` – 当前租户 ID
- `CLIENT_IP` – 客户端 IP
- `REQUEST_URI` – 请求路径
- `REQUEST_METHOD` – HTTP 方法

### 在业务代码中手动设置 MDC（可选）
```java
AppContextHolder.setMdc(AsyncConstants.MdcKeys.USER_ID, "123");
```

### 自动同步（推荐）
在过滤器里调用 `AppContextHolder.syncAppContextToMdc()`，后续所有日志会自动带上上述键值。

---

## 常见错误与调试技巧

| 场景 | 可能原因 | 解决方案 |
|---|---|---|
| **上下文为空**（`AsyncContext.isEmpty()`） | 未在入口线程设置 `AppContextHolder`，或在 `AsyncExecutor` 调用前手动 `clear()` 了 | 确认过滤器/拦截器已执行，或在单元测试中手动 `AppContextHolder.setAppContext(...)` |
| **日志中缺少 MDC** | `AppContextHolder.syncAppContextToMdc()` 未调用，或在子线程里忘记 `restore` | 检查过滤器实现，或在自定义线程池中使用 `ContextPropagatingRunnable`/`Callable`（已在 `VirtualThreadExecutorFactory` 中封装） |
| **异常被吞掉** | `AsyncResult` 未注册 `onFailure`，或 `CompletableFuture` 未调用 `exceptionally` | 始终为异步链路添加错误处理，或在 `AsyncExecutor` 调用后使用 `whenComplete` |
| **虚拟线程未生效** | `quarkus.virtual-threads.enabled` 为 `false`，或使用了 `Executors.newFixedThreadPool` 而非 `VirtualThreadExecutorFactory` | 确认 `application.yaml` 中已开启虚拟线程，所有异步入口均使用 `AsyncExecutor`/`VirtualThreadScheduler` |

### 调试技巧
- **打印当前上下文**：`log.debugv("AsyncContext: {}", AppContextHolder.capture());`
- **查看线程名称**：虚拟线程默认前缀 `vt-async-`（可在 `AsyncConstants.VIRTUAL_THREAD_NAME_PREFIX` 中自定义），帮助区分普通线程。
- **使用 IDE 断点**：在 `ContextPropagatingRunnable.run` 与 `ContextPropagatingCallable.call` 处打断点，确认 `restore` 与 `clear` 正常执行。

---

## 完整示例
下面展示一个典型的业务场景：
1. 在 HTTP 请求过滤器中构建 `AppContext` 并同步 MDC。
2. 在服务层使用 `AsyncExecutor` 执行异步查询并返回 `AsyncResult`。
3. 在控制器里组合多个异步结果并统一返回。

### 1️⃣ 过滤器（已在前文示例）

### 2️⃣ Service 示例
```java
@ApplicationScoped
public class OrderService {
    @Inject
    private OrderRepository orderRepo;

    public AsyncResult<List<OrderDto>> listCurrentUserOrders() {
        return AsyncResult.of(() -> {
            // 自动拥有 AppContext 与 MDC
            Long userId = AppContextHolder.getUserId()
                    .orElseThrow(() -> new IllegalStateException("未登录"));
            return orderRepo.findByUserId(userId);
        })
        .map(orders -> orders.stream()
                .map(OrderDto::fromEntity)
                .collect(Collectors.toList()))
        .onFailure(ex -> log.error("查询订单失败", ex));
    }
}
```

### 3️⃣ REST 控制器
```java
@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
public class OrderResource {
    @Inject
    private OrderService orderService;

    @GET
    public CompletionStage<Response> getOrders() {
        return orderService.listCurrentUserOrders()
                .map(dtos -> Response.ok(dtos).build())
                .onFailure(ex -> log.warn("返回订单时出错", ex))
                .toCompletionStage(); // AsyncResult 提供的便利转换
    }
}
```

运行后，你会在日志中看到类似：
```
2026-01-03 08:45:12.345 TRACE [vt-async-1] (orderService) - AsyncContext: mdc={traceId=abc123, userId=42, tenantId=100}, appContext=AppContext{userId=42, tenantId=100, ...}
2026-01-03 08:45:12.347 INFO  [vt-async-1] (orderService) - 查询到 3 条订单 for userId=42
```

---

## 📌 小结
- **始终在入口处**（过滤器/拦截器）**设置 `AppContext`** 并同步 MDC。
- 使用 **`AsyncExecutor`** 或 **`AsyncResult`** 进行业务异步化，**无需手动管理线程池**。
- 通过 **`AppContextHolder`** 随时获取业务上下文，**日志自动携带链路信息**。
- **虚拟线程** 为高并发提供轻量级实现，确保系统在高负载下仍保持低资源占用。

