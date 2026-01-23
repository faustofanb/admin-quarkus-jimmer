package io.github.faustofan.admin.shared.avaliable;

import io.github.faustofan.admin.shared.avaliable.bulkhead.BulkheadManager;
import io.github.faustofan.admin.shared.avaliable.circuit.CircuitBreakerContext;
import io.github.faustofan.admin.shared.avaliable.circuit.CircuitBreakerManager;
import io.github.faustofan.admin.shared.avaliable.config.AvailabilityConfig;
import io.github.faustofan.admin.shared.avaliable.constants.CircuitBreakerState;
import io.github.faustofan.admin.shared.avaliable.constants.FallbackType;
import io.github.faustofan.admin.shared.avaliable.constants.RetryStrategy;
import io.github.faustofan.admin.shared.avaliable.degradation.DegradationManager;
import io.github.faustofan.admin.shared.avaliable.fallback.FallbackHandler;
import io.github.faustofan.admin.shared.avaliable.ratelimit.DistributedRateLimiter;
import io.github.faustofan.admin.shared.avaliable.ratelimit.LocalRateLimiter;
import io.github.faustofan.admin.shared.avaliable.ratelimit.RateLimiter;
import io.github.faustofan.admin.shared.avaliable.retry.RetryExecutor;
import io.github.faustofan.admin.shared.avaliable.timeout.TimeoutExecutor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 可用性基础设施统一门面
 * <p>
 * 对外统一暴露限流、熔断、降级、回退、重试、超时、隔离等API。
 * <p>
 * 特性：
 * <ul>
 *   <li><b>限流 (Rate Limiting)</b> - 控制请求速率，防止系统过载</li>
 *   <li><b>熔断 (Circuit Breaker)</b> - 快速失败机制，防止级联故障</li>
 *   <li><b>降级 (Degradation)</b> - 服务降级策略，保证核心功能可用</li>
 *   <li><b>回退 (Fallback)</b> - 备选方案，提供兜底响应</li>
 *   <li><b>重试 (Retry)</b> - 失败重试机制，处理临时性故障</li>
 *   <li><b>超时 (Timeout)</b> - 防止请求长时间阻塞</li>
 *   <li><b>隔离 (Bulkhead)</b> - 资源隔离，限制并发执行</li>
 * </ul>
 * <p>
 * 基于 SmallRye Fault Tolerance (MicroProfile Fault Tolerance) 实现，
 * 完全适配 Quarkus 设计哲学。
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 注入 AvailabilityFacade
 * @Inject
 * AvailabilityFacade availabilityFacade;
 *
 * // 1. 限流
 * if (availabilityFacade.tryAcquireRate("api:user:query")) {
 *     return userService.query(request);
 * }
 *
 * // 2. 熔断保护 + 回退
 * User user = availabilityFacade.executeWithCircuitBreaker(
 *     "userService",
 *     () -> userService.getUser(userId),
 *     () -> User.empty() // 回退值
 * );
 *
 * // 3. 重试机制
 * Order order = availabilityFacade.executeWithRetry(
 *     "orderService",
 *     () -> orderService.createOrder(request)
 * );
 *
 * // 4. 超时控制
 * Result result = availabilityFacade.executeWithTimeout(
 *     "externalApi",
 *     () -> externalApiClient.call(),
 *     Duration.ofSeconds(3)
 * );
 *
 * // 5. 隔离舱（并发控制）
 * Data data = availabilityFacade.executeWithBulkhead(
 *     "heavyOperation",
 *     () -> heavyOperationService.process()
 * );
 *
 * // 6. 组合使用：限流 + 熔断 + 超时 + 重试 + 回退
 * Response response = availabilityFacade.protect(
 *     "criticalService",
 *     () -> criticalService.execute(),
 *     () -> Response.degraded() // 回退值
 * );
 * }</pre>
 */
@ApplicationScoped
public class AvailabilityFacade {

    private final AvailabilityConfig config;
    private final LocalRateLimiter localRateLimiter;
    private final DistributedRateLimiter distributedRateLimiter;
    private final CircuitBreakerManager circuitBreakerManager;
    private final FallbackHandler fallbackHandler;
    private final RetryExecutor retryExecutor;
    private final TimeoutExecutor timeoutExecutor;
    private final BulkheadManager bulkheadManager;
    private final DegradationManager degradationManager;

    @Inject
    public AvailabilityFacade(
            AvailabilityConfig config,
            LocalRateLimiter localRateLimiter,
            DistributedRateLimiter distributedRateLimiter,
            CircuitBreakerManager circuitBreakerManager,
            FallbackHandler fallbackHandler,
            RetryExecutor retryExecutor,
            TimeoutExecutor timeoutExecutor,
            BulkheadManager bulkheadManager,
            DegradationManager degradationManager) {
        this.config = config;
        this.localRateLimiter = localRateLimiter;
        this.distributedRateLimiter = distributedRateLimiter;
        this.circuitBreakerManager = circuitBreakerManager;
        this.fallbackHandler = fallbackHandler;
        this.retryExecutor = retryExecutor;
        this.timeoutExecutor = timeoutExecutor;
        this.bulkheadManager = bulkheadManager;
        this.degradationManager = degradationManager;
    }

    // ===========================
    // 限流 API
    // ===========================

    /**
     * 尝试获取限流许可
     */
    public boolean tryAcquireRate(String resourceName) {
        return getRateLimiter().tryAcquire(resourceName);
    }

    /**
     * 尝试获取限流许可（自定义许可数）
     */
    public boolean tryAcquireRate(String resourceName, int permits) {
        return getRateLimiter().tryAcquire(resourceName, permits);
    }

    /**
     * 获取限流许可（阻塞）
     */
    public void acquireRate(String resourceName) {
        getRateLimiter().acquire(resourceName);
    }

    /**
     * 配置限流规则
     */
    public void configureRateLimit(String resourceName, int permitsPerPeriod, Duration period) {
        getRateLimiter().configure(resourceName, permitsPerPeriod, period);
    }

    /**
     * 获取当前限流器
     */
    public RateLimiter getRateLimiter() {
        return config.rateLimit().distributed() ? distributedRateLimiter : localRateLimiter;
    }

    // ===========================
    // 熔断器 API
    // ===========================

    /**
     * 执行带熔断保护的操作
     */
    public <T> T executeWithCircuitBreaker(String name, Supplier<T> supplier) {
        return circuitBreakerManager.execute(name, supplier);
    }

    /**
     * 执行带熔断保护的操作（带回退）
     */
    public <T> T executeWithCircuitBreaker(String name, Supplier<T> supplier, Supplier<T> fallback) {
        return circuitBreakerManager.execute(name, supplier, fallback);
    }

    /**
     * 检查熔断器是否允许请求
     */
    public boolean isCircuitBreakerAllowed(String name) {
        return circuitBreakerManager.isRequestAllowed(name);
    }

    /**
     * 获取熔断器状态
     */
    public CircuitBreakerState getCircuitBreakerState(String name) {
        return circuitBreakerManager.getState(name);
    }

    /**
     * 获取熔断器上下文
     */
    public Optional<CircuitBreakerContext> getCircuitBreakerContext(String name) {
        return circuitBreakerManager.get(name);
    }

    /**
     * 强制打开熔断器
     */
    public void openCircuitBreaker(String name) {
        circuitBreakerManager.forceOpen(name);
    }

    /**
     * 强制关闭熔断器
     */
    public void closeCircuitBreaker(String name) {
        circuitBreakerManager.forceClose(name);
    }

    /**
     * 重置熔断器
     */
    public void resetCircuitBreaker(String name) {
        circuitBreakerManager.reset(name);
    }

    /**
     * 配置熔断器
     */
    public void configureCircuitBreaker(String name, double failureRatio, int requestVolumeThreshold,
                                        Duration delay, int successThreshold) {
        circuitBreakerManager.configure(name, failureRatio, requestVolumeThreshold, delay, successThreshold);
    }

    // ===========================
    // 重试 API
    // ===========================

    /**
     * 执行带重试的操作
     */
    public <T> T executeWithRetry(String name, Supplier<T> supplier) throws Exception {
        return retryExecutor.execute(name, supplier);
    }

    /**
     * 执行带重试的操作（可抛出异常）
     */
    public <T> T executeWithRetry(String name, Callable<T> callable) throws Exception {
        return retryExecutor.execute(name, callable);
    }

    /**
     * 执行带重试的操作（带回退）
     */
    public <T> T executeWithRetryAndFallback(String name, Supplier<T> supplier, Supplier<T> fallback) {
        return retryExecutor.executeWithFallback(name, supplier, fallback);
    }

    /**
     * 配置重试规则
     */
    public void configureRetry(String name, int maxRetries, Duration delay, RetryStrategy strategy) {
        retryExecutor.configure(name, maxRetries, delay, strategy);
    }

    // ===========================
    // 超时 API
    // ===========================

    /**
     * 执行带超时控制的操作
     */
    public <T> T executeWithTimeout(String name, Supplier<T> supplier) {
        return timeoutExecutor.execute(name, supplier);
    }

    /**
     * 执行带超时控制的操作（自定义超时时间）
     */
    public <T> T executeWithTimeout(String name, Supplier<T> supplier, Duration timeout) {
        return timeoutExecutor.execute(name, supplier, timeout);
    }

    /**
     * 执行带超时控制的操作（带回退）
     */
    public <T> T executeWithTimeoutAndFallback(String name, Supplier<T> supplier, Supplier<T> fallback) {
        return timeoutExecutor.executeWithFallback(name, supplier, fallback);
    }

    /**
     * 异步执行带超时控制的操作
     */
    public <T> CompletableFuture<T> executeAsyncWithTimeout(String name, Supplier<T> supplier) {
        return timeoutExecutor.executeAsync(name, supplier);
    }

    /**
     * 配置超时规则
     */
    public void configureTimeout(String name, Duration timeout) {
        timeoutExecutor.configure(name, timeout);
    }

    // ===========================
    // 隔离舱 API
    // ===========================

    /**
     * 执行带隔离保护的操作
     */
    public <T> T executeWithBulkhead(String name, Supplier<T> supplier) {
        return bulkheadManager.execute(name, supplier);
    }

    /**
     * 异步执行带隔离保护的操作
     */
    public <T> CompletableFuture<T> executeAsyncWithBulkhead(String name, Supplier<T> supplier) {
        return bulkheadManager.executeAsync(name, supplier);
    }

    /**
     * 获取隔离舱活跃执行数
     */
    public int getBulkheadActiveCount(String name) {
        return bulkheadManager.getActiveCount(name);
    }

    /**
     * 获取隔离舱可用槽位数
     */
    public int getBulkheadAvailableSlots(String name) {
        return bulkheadManager.getAvailableSlots(name);
    }

    /**
     * 配置隔离舱
     */
    public void configureBulkhead(String name, int maxConcurrentCalls, int waitingTaskQueue) {
        bulkheadManager.configure(name, maxConcurrentCalls, waitingTaskQueue);
    }

    // ===========================
    // 回退 API
    // ===========================

    /**
     * 注册回退函数
     */
    public <T> void registerFallback(String name, Function<Throwable, T> fallback) {
        fallbackHandler.register(name, fallback);
    }

    /**
     * 注册默认回退值
     */
    public <T> void registerDefaultFallback(String name, T defaultValue) {
        fallbackHandler.registerDefault(name, defaultValue);
    }

    /**
     * 执行操作并在失败时回退
     */
    public <T> T executeWithFallback(String name, Supplier<T> supplier, FallbackType fallbackType) {
        return fallbackHandler.executeWithFallback(name, supplier, fallbackType);
    }

    // ===========================
    // 降级 API
    // ===========================

    /**
     * 检查资源是否已降级
     */
    public boolean isDegraded(String resourceName) {
        return degradationManager.isDegraded(resourceName);
    }

    /**
     * 降级特定资源
     */
    public void degrade(String resourceName) {
        degradationManager.degrade(resourceName);
    }

    /**
     * 恢复特定资源
     */
    public void recover(String resourceName) {
        degradationManager.recover(resourceName);
    }

    /**
     * 启用全局降级
     */
    public void enableGlobalDegradation() {
        degradationManager.enableGlobalDegradation();
    }

    /**
     * 禁用全局降级
     */
    public void disableGlobalDegradation() {
        degradationManager.disableGlobalDegradation();
    }

    /**
     * 注册降级实现
     */
    public <T> void registerDegradedImplementation(String resourceName, Supplier<T> degradedImpl) {
        degradationManager.registerDegradedImplementation(resourceName, degradedImpl);
    }

    /**
     * 执行操作（如果降级则使用降级实现）
     */
    public <T> T executeWithDegradation(String resourceName, Supplier<T> normalSupplier, Supplier<T> degradedSupplier) {
        return degradationManager.execute(resourceName, normalSupplier, degradedSupplier);
    }

    // ===========================
    // 组合保护 API
    // ===========================

    /**
     * 全保护模式：限流 + 熔断 + 超时 + 重试 + 回退
     * <p>
     * 按以下顺序应用保护：
     * 1. 限流检查
     * 2. 熔断检查
     * 3. 降级检查
     * 4. 超时控制
     * 5. 重试机制
     * 6. 回退兜底
     */
    public <T> T protect(String resourceName, Supplier<T> supplier) {
        return protect(resourceName, supplier, null);
    }

    /**
     * 全保护模式（带回退）
     */
    public <T> T protect(String resourceName, Supplier<T> supplier, Supplier<T> fallback) {
        // 1. 限流检查
        if (!tryAcquireRate(resourceName)) {
            if (fallback != null) {
                return fallback.get();
            }
            throw new io.github.faustofan.admin.shared.avaliable.exception.RateLimitExceededException(resourceName);
        }

        // 2. 熔断检查
        if (!isCircuitBreakerAllowed(resourceName)) {
            if (fallback != null) {
                return fallback.get();
            }
            throw new io.github.faustofan.admin.shared.avaliable.exception.CircuitBreakerOpenException(resourceName);
        }

        // 3. 降级检查
        if (isDegraded(resourceName)) {
            if (fallback != null) {
                return fallback.get();
            }
            return degradationManager.execute(resourceName, supplier);
        }

        // 4. 执行：超时 + 重试 + 熔断 + 回退
        try {
            return timeoutExecutor.execute(resourceName, (Callable<T>) () -> {
                try {
                    return retryExecutor.execute(resourceName, (Callable<T>) () -> {
                        return circuitBreakerManager.execute(resourceName, supplier, fallback);
                    });
                } catch (Exception e) {
                    if (e instanceof RuntimeException) {
                        throw (RuntimeException) e;
                    }
                    throw new RuntimeException("Operation failed", e);
                }
            });
        } catch (Exception e) {
            if (fallback != null) {
                return fallback.get();
            }
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException("Protected operation failed", e);
        }
    }

    /**
     * 轻量保护模式：熔断 + 回退
     */
    public <T> T protectLight(String resourceName, Supplier<T> supplier, Supplier<T> fallback) {
        return circuitBreakerManager.execute(resourceName, supplier, fallback);
    }

    /**
     * 标准保护模式：熔断 + 重试 + 回退
     */
    public <T> T protectStandard(String resourceName, Supplier<T> supplier, Supplier<T> fallback) {
        try {
            return retryExecutor.execute(resourceName, (Callable<T>) () -> {
                return circuitBreakerManager.execute(resourceName, supplier, fallback);
            });
        } catch (Exception e) {
            if (fallback != null) {
                return fallback.get();
            }
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException("Protected operation failed", e);
        }
    }

    // ===========================
    // 配置和状态 API
    // ===========================

    /**
     * 获取配置
     */
    public AvailabilityConfig getConfig() {
        return config;
    }

    /**
     * 检查可用性保护是否启用
     */
    public boolean isEnabled() {
        return config.enabled();
    }

    /**
     * 获取所有熔断器状态
     */
    public Map<String, CircuitBreakerContext> getAllCircuitBreakers() {
        return circuitBreakerManager.getAllContexts();
    }

    /**
     * 获取降级状态
     */
    public DegradationManager.DegradationStatus getDegradationStatus() {
        return degradationManager.getStatus();
    }

    /**
     * 重置所有保护组件
     */
    public void resetAll() {
        circuitBreakerManager.resetAll();
        getRateLimiter().resetAll();
    }
}
