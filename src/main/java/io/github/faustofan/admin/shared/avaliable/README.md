# 可用性基础设施 (Availability Infrastructure)

企业级服务保护基础设施，提供限流、熔断、降级、回退、重试、超时和隔离等七大保护机制。

## ✨ 特性

- 🎯 **限流 (Rate Limiting)** - 控制请求速率，防止系统过载
- 🛡️ **熔断 (Circuit Breaker)** - 快速失败机制，防止级联故障
- 📉 **降级 (Degradation)** - 服务降级策略，保证核心功能可用
- 🔄 **回退 (Fallback)** - 备选方案，提供兜底响应
- ⚡ **重试 (Retry)** - 失败重试机制，处理临时性故障
- ⏱️ **超时 (Timeout)** - 防止请求长时间阻塞
- 🚧 **隔离 (Bulkhead)** - 资源隔离，限制并发执行

## 🚀 快速开始

### 1. 声明式注解（推荐）

```java
@ApplicationScoped
public class UserService {

    // 熔断保护 + 回退
    @CircuitBreaker(name = "userService", failureRatio = 0.5)
    @Fallback(fallbackMethod = "getUserFallback")
    public User getUser(Long userId) {
        return externalUserService.getUser(userId);
    }

    private User getUserFallback(Long userId) {
        return User.empty();
    }

    // 限流保护
    @RateLimit(permits = 100, window = "PT1S")
    public List<User> queryUsers() {
        return userRepository.findAll();
    }

    // 全保护模式
    @Protect(mode = ProtectMode.FULL, fallbackMethod = "createFallback")
    public User createUser(CreateUserRequest request) {
        return userRepository.save(request.toEntity());
    }

    private User createFallback(CreateUserRequest request) {
        return User.pending(request);
    }
}
```

### 2. 编程式 API

```java
@ApplicationScoped
public class OrderService {

    @Inject
    AvailabilityFacade availability;

    public Order createOrder(CreateOrderRequest request) {
        return availability.protect(
            "order:create",
            () -> orderService.create(request),
            () -> Order.pending(request)
        );
    }
}
```

## 📚 文档

| 文档 | 说明 |
|------|------|
| [**注解使用指南**](docs/availability_annotations_guide.md) | 声明式注解详细使用说明（推荐） |
| [**API使用指南**](docs/availability_usage_guide.md) | 编程式API详细使用说明 |
| [**注解支持总结**](docs/availability_annotations_summary.md) | 注解功能总结和对比 |

## 💡 使用示例

### 限流

```java
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
```

### 熔断

```java
@CircuitBreaker(
    name = "externalPaymentService",
    failureRatio = 0.5,
    requestVolumeThreshold = 20,
    delay = "PT5S",
    successThreshold = 3
)
@Fallback(fallbackMethod = "paymentFallback")
public PaymentResult processPayment(PaymentRequest request) {
    return externalPaymentService.pay(request);
}
```

### 重试

```java
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
```

### 超时

```java
@Timeout(
    name = "externalApi",
    duration = "PT3S"
)
public ApiResponse callExternalApi(ApiRequest request) {
    return externalApiClient.call(request);
}
```

### 隔离舱

```java
@Bulkhead(
    name = "reportGeneration",
    maxConcurrentCalls = 10,
    waitingTaskQueue = 20,
    waitTimeout = "PT5S"
)
public Report generateReport(ReportRequest request) {
    return reportGenerator.generate(request);
}
```

### 组合保护

```java
// 限流 + 熔断 + 重试 + 超时
@RateLimit(permits = 50)
@CircuitBreaker(failureRatio = 0.6)
@Retry(maxRetries = 3)
@Timeout(duration = "PT5S")
@Fallback(fallbackMethod = "fallback")
public Result execute(Request request) {
    return externalService.process(request);
}

// 或使用 @Protect 简化
@Protect(mode = ProtectMode.FULL, fallbackMethod = "fallback")
public Result execute(Request request) {
    return externalService.process(request);
}
```

## ⚙️ 配置

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
```

## 🎯 核心组件

### 注解
- `@RateLimit` - 限流保护
- `@CircuitBreaker` - 熔断保护
- `@Retry` - 重试机制
- `@Timeout` - 超时控制
- `@Bulkhead` - 隔离舱
- `@Fallback` - 回退处理
- `@Protect` - 组合保护

### 统一门面
- `AvailabilityFacade` - 提供编程式 API

### 管理器
- `CircuitBreakerManager` - 熔断器管理
- `BulkheadManager` - 隔离舱管理
- `DegradationManager` - 降级管理
- `FallbackHandler` - 回退处理器
- `RetryExecutor` - 重试执行器
- `TimeoutExecutor` - 超时执行器

### 限流器
- `LocalRateLimiter` - 本地限流（JVM）
- `DistributedRateLimiter` - 分布式限流（Redis）

## 🏗️ 架构

```
注解层
   ↓
拦截器层
   ↓
门面层 (AvailabilityFacade)
   ↓
管理器层 (CircuitBreakerManager, BulkheadManager, etc.)
   ↓
执行器层 (RetryExecutor, TimeoutExecutor, etc.)
```

## 📊 监控

```java
@Inject
AvailabilityFacade availability;

// 查询熔断器状态
CircuitBreakerState state = availability.getCircuitBreakerState("userService");

// 查询所有熔断器
Map<String, CircuitBreakerContext> all = availability.getAllCircuitBreakers();

// 查询降级状态
DegradationStatus status = availability.getDegradationStatus();

// 查询隔离舱状态
int active = availability.getBulkheadActiveCount("heavyOperation");
int available = availability.getBulkheadAvailableSlots("heavyOperation");
```

## 🔧 技术栈

- **Quarkus** 3.30.5
- **SmallRye Fault Tolerance**
- **Quarkus Redis** (分布式限流)
- **JDK** 21 (虚拟线程)
- **CDI** (依赖注入)

## 💪 设计亮点

1. ✅ **完全适配 Quarkus** - 使用 CDI、ConfigMapping、Mutiny、虚拟线程
2. ✅ **统一门面模式** - `AvailabilityFacade` 作为唯一对外接口
3. ✅ **无魔法字符串** - 所有常量和枚举集中管理
4. ✅ **声明式注解** - 简洁优雅的代码风格
5. ✅ **组合保护模式** - 提供全保护、标准保护、轻量保护
6. ✅ **抽象合理** - 职责单一，边界清晰

## 📖 最佳实践

1. **优先使用注解** - 代码简洁，易于维护
2. **合理设置阈值** - 根据实际业务场景调整参数
3. **提供有意义的回退** - 回退值应该是合理的默认值或缓存值
4. **监控和告警** - 监控熔断器打开/关闭事件和限流拒绝率
5. **分级保护** - 核心服务使用全保护，非核心服务使用轻量保护
6. **避免过度保护** - 根据实际需求选择合适的保护策略

## 🎓 示例

完整的使用示例请参考：
- [注解使用指南](docs/availability_annotations_guide.md)
- [API使用指南](docs/availability_usage_guide.md)

## 📜 License

MIT License
