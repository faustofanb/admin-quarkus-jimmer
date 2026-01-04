## 概述

可用性基础设施提供了一套完整的服务保护机制，包括限流、熔断、降级、回退、重试、超时和隔离等功能。基于 Quarkus 和 SmallRye Fault Tolerance 实现，完全适配 Quarkus 设计哲学。

## 使用方式

可用性基础设施提供两种使用方式：

### 1. 声明式注解（推荐）

使用注解直接在方法上声明保护机制，简洁直观：

```java
@CircuitBreaker(name = "userService", failureRatio = 0.5)
@Fallback(fallbackMethod = "getUserFallback")
public User getUser(Long userId) {
    return externalUserService.getUser(userId);
}

private User getUserFallback(Long userId) {
    return User.empty();
}
```

详细使用请参考：[**注解使用指南**](availability_annotations_guide.md)

### 2. 编程式 API

通过 `AvailabilityFacade` 编程式调用，灵活可控：

```java
@Inject
AvailabilityFacade availability;

public User getUser(Long userId) {
    return availability.executeWithCircuitBreaker(
        "userService",
        () -> externalUserService.getUser(userId),
        () -> User.empty()
    );
}
```

两种方式可以混合使用，根据场景选择最合适的方式。

## 核心功能


### 1. 限流 (Rate Limiting)

控制请求速率，防止系统过载。

**支持的算法：**
- 固定窗口 (FIXED_WINDOW)
- 滑动窗口 (SLIDING_WINDOW) - 推荐
- 令牌桶 (TOKEN_BUCKET)
- 漏桶 (LEAKY_BUCKET)
- 并发限流 (CONCURRENT)

**使用示例：**

```java
@Inject
AvailabilityFacade availability;

// 检查是否可以执行请求
if (availability.tryAcquireRate("api:user:query")) {
    return userService.query(request);
} else {
    throw new RateLimitExceededException("请求过于频繁");
}

// 配置限流规则
availability.configureRateLimit("api:order:create", 10, Duration.ofSeconds(1));
```

**支持本地和分布式限流：**
- 本地限流：基于 JVM 内存，适用于单实例场景
- 分布式限流：基于 Redis，适用于多实例集群场景

### 2. 熔断 (Circuit Breaker)

快速失败机制，防止级联故障。

**熔断器状态：**
- CLOSED（关闭）：正常工作状态
- OPEN（打开）：熔断状态，快速失败
- HALF_OPEN（半开）：尝试恢复状态

**使用示例：**

```java
// 基本用法
User user = availability.executeWithCircuitBreaker(
    "userService",
    () -> userService.getUser(userId)
);

// 带回退
User user = availability.executeWithCircuitBreaker(
    "userService",
    () -> userService.getUser(userId),
    () -> User.empty() // 熔断时返回空用户
);

// 配置熔断器
availability.configureCircuitBreaker(
    "externalApi",
    0.5,              // 50% 失败率触发熔断
    20,               // 最少20个请求才计算失败率
    Duration.ofSeconds(5),  // 5秒后进入半开状态
    3                 // 半开状态需要3次成功才关闭
);

// 手动控制
availability.openCircuitBreaker("userService");   // 强制打开
availability.closeCircuitBreaker("userService");  // 强制关闭
availability.resetCircuitBreaker("userService");  // 重置
```

### 3. 重试 (Retry)

失败重试机制，处理临时性故障。

**重试策略：**
- FIXED：固定延迟
- EXPONENTIAL：指数退避（推荐）
- FIBONACCI：斐波那契退避
- RANDOM：随机延迟
- IMMEDIATE：立即重试

**使用示例：**

```java
// 基本用法
Order order = availability.executeWithRetry(
    "orderService",
    () -> orderService.createOrder(request)
);

// 带回退
Order order = availability.executeWithRetryAndFallback(
    "orderService",
    () -> orderService.createOrder(request),
    () -> Order.pending() // 重试失败后返回待处理订单
);

// 配置重试规则
availability.configureRetry(
    "externalApi",
    3,                          // 最多重试3次
    Duration.ofMillis(200),     // 初始延迟200ms
    RetryStrategy.EXPONENTIAL   // 使用指数退避
);
```

### 4. 超时 (Timeout)

防止请求长时间阻塞。

**使用示例：**

```java
// 使用默认超时
Result result = availability.executeWithTimeout(
    "externalApi",
    () -> externalApiClient.call()
);

// 自定义超时时间
Result result = availability.executeWithTimeout(
    "externalApi",
    () -> externalApiClient.call(),
    Duration.ofSeconds(3)
);

// 带回退
Result result = availability.executeWithTimeoutAndFallback(
    "externalApi",
    () -> externalApiClient.call(),
    () -> Result.cached() // 超时返回缓存结果
);

// 异步执行
CompletableFuture<Result> future = availability.executeAsyncWithTimeout(
    "externalApi",
    () -> externalApiClient.call()
);
```

### 5. 隔离舱 (Bulkhead)

资源隔离，限制并发执行数量。

**使用示例：**

```java
// 基本用法
Data data = availability.executeWithBulkhead(
    "heavyOperation",
    () -> heavyOperationService.process()
);

// 异步执行
CompletableFuture<Data> future = availability.executeAsyncWithBulkhead(
    "heavyOperation",
    () -> heavyOperationService.process()
);

// 配置隔离舱
availability.configureBulkhead(
    "dbOperation",
    10,  // 最大并发数
    20   // 等待队列大小
);

// 查询状态
int active = availability.getBulkheadActiveCount("dbOperation");
int available = availability.getBulkheadAvailableSlots("dbOperation");
```

### 6. 降级 (Degradation)

服务降级策略，保证核心功能可用。

**使用示例：**

```java
// 降级特定服务
availability.degrade("recommendService");

// 执行时自动使用降级实现
List<Item> items = availability.executeWithDegradation(
    "recommendService",
    () -> recommendService.getRecommendations(userId),
    () -> Collections.emptyList() // 降级时返回空列表
);

// 注册降级实现
availability.registerDegradedImplementation(
    "recommendService",
    () -> recommendService.getDefaultRecommendations()
);

// 恢复服务
availability.recover("recommendService");

// 全局降级（紧急情况）
availability.enableGlobalDegradation();
availability.disableGlobalDegradation();
```

### 7. 回退 (Fallback)

备选方案，提供兜底响应。

**回退类型：**
- EMPTY：返回空值
- DEFAULT_VALUE：返回默认值
- CACHED：返回缓存值
- DEGRADED：切换到降级服务
- THROW_EXCEPTION：抛出异常
- REDIRECT：重定向到备用服务

**使用示例：**

```java
// 注册回退函数
availability.registerFallback("userService", throwable -> {
    log.error("Failed to get user", throwable);
    return User.guest();
});

// 注册默认回退值
availability.registerDefaultFallback("configService", Config.defaults());

// 使用回退
Config config = availability.executeWithFallback(
    "configService",
    () -> configService.loadRemoteConfig(),
    FallbackType.CACHED
);
```

## 组合使用

### 全保护模式

应用所有保护机制：限流 + 熔断 + 降级 + 超时 + 重试 + 回退

```java
Response response = availability.protect(
    "criticalService",
    () -> criticalService.execute(request),
    () -> Response.degraded() // 兜底响应
);
```

执行顺序：
1. 限流检查
2. 熔断检查
3. 降级检查
4. 超时控制
5. 重试机制
6. 回退兜底

### 标准保护模式

熔断 + 重试 + 回退

```java
Result result = availability.protectStandard(
    "standardService",
    () -> standardService.execute(),
    () -> Result.fallback()
);
```

### 轻量保护模式

仅熔断 + 回退

```java
Data data = availability.protectLight(
    "lightService",
    () -> lightService.execute(),
    () -> Data.empty()
);
```

## 配置说明

在 `application.yaml` 中配置：

```yaml
app:
  availability:
    enabled: true
    
    rate-limit:
      enabled: true
      algorithm: SLIDING_WINDOW
      default-permits: 100
      default-window: PT1S
      distributed: false
    
    circuit-breaker:
      enabled: true
      failure-ratio: 0.5
      request-volume-threshold: 20
      delay: PT5S
      success-threshold: 5
    
    retry:
      enabled: true
      max-retries: 3
      delay: PT0.2S
      strategy: EXPONENTIAL
    
    timeout:
      enabled: true
      default-duration: PT5S
    
    bulkhead:
      enabled: true
      max-concurrent-calls: 10
      waiting-task-queue: 10
    
    degradation:
      enabled: true
      force-degraded: false
```

详细配置请参考 `availability-config-example.yaml`。

## 监控和管理

### 查询状态

```java
// 熔断器状态
CircuitBreakerState state = availability.getCircuitBreakerState("userService");
Map<String, CircuitBreakerContext> all = availability.getAllCircuitBreakers();

// 降级状态
DegradationStatus status = availability.getDegradationStatus();

// 隔离舱状态
int active = availability.getBulkheadActiveCount("heavyOperation");
```

### 重置

```java
// 重置特定熔断器
availability.resetCircuitBreaker("userService");

// 重置所有组件
availability.resetAll();
```

# 可用性基础设施注解使用指南

## 概述

可用性基础设施现在提供了声明式注解支持，让开发者可以通过简单的注解来应用各种保护机制，无需手动调用 `AvailabilityFacade`。

## 可用注解列表

1. **@RateLimit** - 限流保护
2. **@CircuitBreaker** - 熔断保护
3. **@Retry** - 重试机制
4. **@Timeout** - 超时控制
5. **@Bulkhead** - 隔离舱（并发控制）
6. **@Fallback** - 回退处理
7. **@Protect** - 组合保护

## 使用示例

### 1. @RateLimit - 限流保护

控制方法的调用频率，防止系统过载。

```java
@ApplicationScoped
public class UserService {

    // 每秒最多100次请求
    @RateLimit(name = "api:user:query", permits = 100, window = "PT1S")
    public List<User> queryUsers(QueryRequest request) {
        return userRepository.findAll(request);
    }

    // 自定义算法和分布式限流
    @RateLimit(
        name = "api:user:create",
        permits = 10,
        window = "PT1S",
        algorithm = RateLimitAlgorithm.SLIDING_WINDOW,
        distributed = true  // 使用 Redis 分布式限流
    )
    public User createUser(CreateUserRequest request) {
        return userRepository.save(request.toEntity());
    }
}
```

### 2. @CircuitBreaker - 熔断保护

防止级联故障，快速失败。

```java
@ApplicationScoped
public class OrderService {

    // 基本熔断器
    @CircuitBreaker(
        name = "externalPaymentService",
        failureRatio = 0.5,              // 50% 失败率触发熔断
        requestVolumeThreshold = 20,     // 最少20个请求才计算失败率
        delay = "PT5S",                  // 5秒后进入半开状态
        successThreshold = 3             // 半开状态需要3次成功才关闭
    )
    public PaymentResult processPayment(PaymentRequest request) {
        return externalPaymentService.pay(request);
    }

    // 带回退的熔断器
    @CircuitBreaker(name = "inventoryService")
    @Fallback(fallbackMethod = "getInventoryFallback")
    public Inventory getInventory(Long productId) {
        return inventoryService.getInventory(productId);
    }

    // 回退方法
    private Inventory getInventoryFallback(Long productId) {
        return Inventory.unavailable();
    }
}
```

### 3. @Retry - 重试机制

自动重试失败的操作。

```java
@ApplicationScoped
public class NotificationService {

    // 指数退避重试
    @Retry(
        name = "emailNotification",
        maxRetries = 3,
        delay = "PT0.2S",
        strategy = RetryStrategy.EXPONENTIAL,
        jitter = "PT0.05S"
    )
    public void sendEmail(EmailRequest request) {
        emailClient.send(request);
    }

    // 固定延迟重试
    @Retry(
        name = "smsNotification",
        maxRetries = 5,
        delay = "PT1S",
        strategy = RetryStrategy.FIXED
    )
    public void sendSms(SmsRequest request) {
        smsClient.send(request);
    }
}
```

### 4. @Timeout - 超时控制

防止方法执行时间过长。

```java
@ApplicationScoped
public class ExternalApiService {

    // 3秒超时
    @Timeout(name = "externalApi", duration = "PT3S")
    public ApiResponse callExternalApi(ApiRequest request) {
        return externalApiClient.call(request);
    }

    // 组合使用：超时 + 重试
    @Timeout(duration = "PT2S")
    @Retry(maxRetries = 3)
    public Data fetchData(String key) {
        return externalDataSource.fetch(key);
    }
}
```

### 5. @Bulkhead - 隔离舱

限制并发执行数量，防止资源耗尽。

```java
@ApplicationScoped
public class ReportService {

    // 最多10个并发执行
    @Bulkhead(
        name = "reportGeneration",
        maxConcurrentCalls = 10,
        waitingTaskQueue = 20,
        waitTimeout = "PT5S"
    )
    public Report generateReport(ReportRequest request) {
        return reportGenerator.generate(request);
    }

    // 组合使用：隔离舱 + 超时
    @Bulkhead(maxConcurrentCalls = 5)
    @Timeout(duration = "PT30S")
    public LargeReport generateLargeReport(LargeReportRequest request) {
        return reportGenerator.generateLarge(request);
    }
}
```

### 6. @Fallback - 回退处理

提供失败时的备选方案。

```java
@ApplicationScoped
public class ProductService {

    @CircuitBreaker(name = "productRecommendation")
    @Fallback(
        fallbackMethod = "getRecommendationsFallback",
        type = FallbackType.CACHED
    )
    public List<Product> getRecommendations(Long userId) {
        return recommendationEngine.getRecommendations(userId);
    }

    // 回退方法签名必须与原方法一致
    private List<Product> getRecommendationsFallback(Long userId) {
        // 返回默认推荐或空列表
        return Collections.emptyList();
    }
}
```

### 7. @Protect - 组合保护

一个注解应用多种保护机制。

```java
@ApplicationScoped
public class CriticalService {

    // 全保护模式：限流 + 熔断 + 降级 + 超时 + 重试 + 回退
    @Protect(
        name = "criticalOperation",
        mode = ProtectMode.FULL,
        fallbackMethod = "criticalOperationFallback"
    )
    public Response executeCriticalOperation(Request request) {
        return criticalBusinessLogic.execute(request);
    }

    private Response criticalOperationFallback(Request request) {
        return Response.degraded();
    }

    // 标准保护模式：熔断 + 重试 + 回退
    @Protect(
        name = "standardOperation",
        mode = ProtectMode.STANDARD,
        fallbackMethod = "standardOperationFallback"
    )
    public Result executeStandardOperation(Input input) {
        return standardBusinessLogic.execute(input);
    }

    private Result standardOperationFallback(Input input) {
        return Result.empty();
    }

    // 轻量保护模式：熔断 + 回退
    @Protect(
        name = "lightOperation",
        mode = ProtectMode.LIGHT,
        fallbackMethod = "lightOperationFallback"
    )
    public Data executeLightOperation(Query query) {
        return lightBusinessLogic.execute(query);
    }

    private Data lightOperationFallback(Query query) {
        return Data.defaultData();
    }
}
```

## 组合使用

注解可以组合使用，实现多重保护：

```java
@ApplicationScoped
public class ComplexService {

    // 组合示例1：限流 + 熔断 + 重试
    @RateLimit(permits = 50, window = "PT1S")
    @CircuitBreaker(name = "complexOp1", failureRatio = 0.6)
    @Retry(maxRetries = 2, strategy = RetryStrategy.EXPONENTIAL)
    @Fallback(fallbackMethod = "operation1Fallback")
    public Result operation1(Request request) {
        return externalService.process(request);
    }

    private Result operation1Fallback(Request request) {
        return Result.cached();
    }

    // 组合示例2：限流 + 超时 + 隔离舱
    @RateLimit(permits = 100)
    @Timeout(duration = "PT5S")
    @Bulkhead(maxConcurrentCalls = 10)
    public Data operation2(Query query) {
        return heavyService.query(query);
    }

    // 组合示例3：使用 @Protect 简化
    @Protect(mode = ProtectMode.FULL, fallbackMethod = "operation3Fallback")
    public Response operation3(Input input) {
        return criticalService.process(input);
    }

    private Response operation3Fallback(Input input) {
        return Response.degraded();
    }
}
```

## 拦截器优先级

拦截器执行顺序（从外到内）：

1. **@Protect** (优先级: PLATFORM_BEFORE)
2. **@RateLimit** (优先级: PLATFORM_BEFORE + 100)
3. **@CircuitBreaker** (优先级: PLATFORM_BEFORE + 200)
4. **@Retry** (优先级: PLATFORM_BEFORE + 300)
5. **@Timeout** (优先级: PLATFORM_BEFORE + 400)
6. **@Bulkhead** (优先级: PLATFORM_BEFORE + 500)

执行流程：
```
@Protect → @RateLimit → @CircuitBreaker → @Retry → @Timeout → @Bulkhead → 业务方法
```

## 注意事项

### 1. 回退方法要求
- 回退方法必须在同一个类中
- 方法签名（参数类型）必须与原方法一致
- 回退方法可以是 `private` 或 `public`
- 回退方法不能是 `static`

### 2. 注解位置
- 可以放在方法上或类上
- 放在类上时，对类中所有公共方法生效
- 方法级注解优先于类级注解

### 3. 命名规范
- 如果不指定 `name` 属性，将使用 `类名.方法名` 作为默认名称
- 建议显式指定有意义的名称，便于监控和管理

### 4. 分布式限流
- 使用分布式限流需要配置 Redis
- 分布式限流适用于多实例场景
- 本地限流适用于单实例场景

### 5. 性能考虑
- 避免对所有方法都启用所有保护机制
- 根据实际需求选择合适的保护策略
- 合理设置超时时间和重试次数

## 完整示例

```java
@ApplicationScoped
public class OrderService {

    @Inject
    PaymentService paymentService;

    @Inject
    InventoryService inventoryService;

    @Inject
    NotificationService notificationService;

    /**
     * 创建订单 - 全保护模式
     */
    @Protect(
        name = "order:create",
        mode = ProtectMode.FULL,
        fallbackMethod = "createOrderFallback"
    )
    public Order createOrder(CreateOrderRequest request) {
        // 1. 检查库存
        Inventory inventory = inventoryService.checkInventory(request.getProductId());
        if (!inventory.isAvailable()) {
            throw new BusinessException("库存不足");
        }

        // 2. 创建订单
        Order order = orderRepository.save(request.toEntity());

        // 3. 处理支付
        PaymentResult payment = paymentService.processPayment(
            new PaymentRequest(order.getId(), order.getTotalAmount())
        );

        // 4. 发送通知
        notificationService.sendOrderConfirmation(order);

        return order;
    }

    private Order createOrderFallback(CreateOrderRequest request) {
        // 返回待处理订单
        return Order.pending(request);
    }

    /**
     * 查询订单 - 轻量保护
     */
    @RateLimit(permits = 1000, window = "PT1S")
    @CircuitBreaker(name = "order:query")
    public Order getOrder(Long orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new NotFoundException("订单不存在"));
    }

    /**
     * 取消订单 - 标准保护
     */
    @Protect(
        name = "order:cancel",
        mode = ProtectMode.STANDARD,
        fallbackMethod = "cancelOrderFallback"
    )
    public void cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new NotFoundException("订单不存在"));

        // 退款
        paymentService.refund(order);

        // 恢复库存
        inventoryService.restoreInventory(order);

        // 更新订单状态
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
    }

    private void cancelOrderFallback(Long orderId) {
        // 记录失败，稍后重试
        failedCancellationQueue.add(orderId);
    }
}
```

## 配置

在 `application.yaml` 中配置全局默认值：

```yaml
app:
  availability:
    enabled: true
    rate-limit:
      enabled: true
      default-permits: 100
      default-window: PT1S
    circuit-breaker:
      enabled: true
      failure-ratio: 0.5
      request-volume-threshold: 20
    retry:
      enabled: true
      max-retries: 3
      strategy: EXPONENTIAL
```

注解中的配置会覆盖全局配置。

## 监控和管理

```java
@Inject
AvailabilityFacade availability;

// 查询熔断器状态
CircuitBreakerState state = availability.getCircuitBreakerState("order:create");

// 查询所有熔断器
Map<String, CircuitBreakerContext> all = availability.getAllCircuitBreakers();

// 手动打开熔断器
availability.openCircuitBreaker("order:create");

// 重置熔断器
availability.resetCircuitBreaker("order:create");
```

## 总结

- ✅ 使用注解简化代码，提高可读性
- ✅ 声明式配置，易于维护
- ✅ 支持组合使用，灵活强大
- ✅ 提供回退机制，保证服务可用
- ✅ 完全兼容编程式 API

## 最佳实践

1. **合理设置阈值**
   - 根据实际业务场景调整失败率、请求量阈值等参数
   - 避免过于敏感导致频繁熔断

2. **提供有意义的回退**
   - 回退值应该是合理的默认值或缓存值
   - 避免返回 null 或空对象导致 NPE

3. **监控和告警**
   - 监控熔断器打开/关闭事件
   - 监控限流拒绝率
   - 设置告警阈值

4. **分级保护**
   - 核心服务使用全保护模式
   - 非核心服务使用标准或轻量保护模式
   - 根据重要程度选择合适的保护级别

5. **测试降级场景**
   - 定期进行降级演练
   - 验证降级实现的正确性
   - 确保降级不影响核心功能

6. **避免过度保护**
   - 不要对所有接口都启用所有保护机制
   - 根据实际需求选择合适的保护策略
   - 避免性能开销

## 常见问题

### Q: 如何选择限流算法？

A: 推荐使用滑动窗口（SLIDING_WINDOW），它能更平滑地处理流量突发。如果需要严格控制速率，使用令牌桶（TOKEN_BUCKET）。

### Q: 分布式限流和本地限流的区别？

A: 本地限流基于 JVM 内存，只在单个实例生效；分布式限流基于 Redis，在所有实例间共享限流计数，适用于集群环境。

### Q: 熔断器什么时候会打开？

A: 当请求数量达到 `requestVolumeThreshold` 且失败率超过 `failureRatio` 时，熔断器会打开。

### Q: 如何调试熔断器？

A: 启用日志，查看熔断器状态变化；使用 `getAllCircuitBreakers()` 查询所有熔断器状态。

### Q: 重试会导致雪崩吗？

A: 使用指数退避策略和抖动可以避免重试风暴。建议设置合理的最大重试次数和最大延迟时间。

### Q: 降级和回退的区别？

A: 降级是主动的服务质量下降，通常是手动触发或自动触发；回退是被动的失败处理，在操作失败时自动执行。

## 扩展

如需更复杂的容错机制，可以：

1. 自定义限流算法
2. 实现自定义回退策略
3. 集成外部监控系统
4. 添加自定义指标收集

详细信息请参考源代码和 JavaDoc。
