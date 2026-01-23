package io.github.faustofan.admin.shared.avaliable.config;

import io.github.faustofan.admin.shared.avaliable.constants.RateLimitAlgorithm;
import io.github.faustofan.admin.shared.avaliable.constants.RetryStrategy;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.time.Duration;

/**
 * 可用性配置映射接口
 * <p>
 * 使用 Quarkus ConfigMapping 进行类型安全的配置绑定
 * <p>
 * 配置示例 (application.yaml):
 * <pre>
 * app:
 *   availability:
 *     enabled: true
 *     rate-limit:
 *       enabled: true
 *       algorithm: SLIDING_WINDOW
 *       default-permits: 100
 *       default-window: PT1S
 *     circuit-breaker:
 *       enabled: true
 *       failure-ratio: 0.5
 *       request-volume-threshold: 20
 *       delay: PT5S
 *       success-threshold: 5
 *     retry:
 *       enabled: true
 *       max-retries: 3
 *       delay: PT0.2S
 *       strategy: EXPONENTIAL
 *     timeout:
 *       enabled: true
 *       default-duration: PT5S
 *     bulkhead:
 *       enabled: true
 *       max-concurrent-calls: 10
 *       waiting-task-queue: 10
 * </pre>
 */
@ConfigMapping(prefix = "app.availability")
public interface AvailabilityConfig {

    /**
     * 是否启用可用性保护
     */
    @WithName("enabled")
    @WithDefault("true")
    boolean enabled();

    /**
     * 限流配置
     */
    @WithName("rate-limit")
    RateLimitConfig rateLimit();

    /**
     * 熔断器配置
     */
    @WithName("circuit-breaker")
    CircuitBreakerConfig circuitBreaker();

    /**
     * 重试配置
     */
    @WithName("retry")
    RetryConfig retry();

    /**
     * 超时配置
     */
    @WithName("timeout")
    TimeoutConfig timeout();

    /**
     * 隔离舱配置
     */
    @WithName("bulkhead")
    BulkheadConfig bulkhead();

    /**
     * 降级配置
     */
    @WithName("degradation")
    DegradationConfig degradation();

    /**
     * 限流配置
     */
    interface RateLimitConfig {

        /**
         * 是否启用限流
         */
        @WithName("enabled")
        @WithDefault("true")
        boolean enabled();

        /**
         * 限流算法
         */
        @WithName("algorithm")
        @WithDefault("SLIDING_WINDOW")
        RateLimitAlgorithm algorithm();

        /**
         * 默认每窗口允许的请求数
         */
        @WithName("default-permits")
        @WithDefault("100")
        int defaultPermits();

        /**
         * 默认时间窗口
         */
        @WithName("default-window")
        @WithDefault("PT1S")
        Duration defaultWindow();

        /**
         * 最小请求间隔
         */
        @WithName("min-spacing")
        @WithDefault("PT0S")
        Duration minSpacing();

        /**
         * 是否使用分布式限流（Redis）
         */
        @WithName("distributed")
        @WithDefault("false")
        boolean distributed();

        /**
         * 分布式限流同步间隔
         */
        @WithName("sync-interval")
        @WithDefault("PT1S")
        Duration syncInterval();
    }

    /**
     * 熔断器配置
     */
    interface CircuitBreakerConfig {

        /**
         * 是否启用熔断器
         */
        @WithName("enabled")
        @WithDefault("true")
        boolean enabled();

        /**
         * 失败率阈值（0.0-1.0）
         */
        @WithName("failure-ratio")
        @WithDefault("0.5")
        double failureRatio();

        /**
         * 请求量阈值（用于计算失败率的最小请求数）
         */
        @WithName("request-volume-threshold")
        @WithDefault("20")
        int requestVolumeThreshold();

        /**
         * 熔断延迟时间（从 OPEN 状态到 HALF_OPEN 状态的等待时间）
         */
        @WithName("delay")
        @WithDefault("PT5S")
        Duration delay();

        /**
         * 成功阈值（半开状态需要成功的请求数才能关闭熔断器）
         */
        @WithName("success-threshold")
        @WithDefault("5")
        int successThreshold();

        /**
         * 滚动窗口大小
         */
        @WithName("rolling-window")
        @WithDefault("10")
        int rollingWindow();

        /**
         * 失败时应跳过的异常类型
         */
        @WithName("skip-on")
        @WithDefault("")
        String skipOn();

        /**
         * 应视为失败的异常类型
         */
        @WithName("fail-on")
        @WithDefault("")
        String failOn();
    }

    /**
     * 重试配置
     */
    interface RetryConfig {

        /**
         * 是否启用重试
         */
        @WithName("enabled")
        @WithDefault("true")
        boolean enabled();

        /**
         * 最大重试次数
         */
        @WithName("max-retries")
        @WithDefault("3")
        int maxRetries();

        /**
         * 重试延迟
         */
        @WithName("delay")
        @WithDefault("PT0.2S")
        Duration delay();

        /**
         * 重试策略
         */
        @WithName("strategy")
        @WithDefault("EXPONENTIAL")
        RetryStrategy strategy();

        /**
         * 抖动时间
         */
        @WithName("jitter")
        @WithDefault("PT0.05S")
        Duration jitter();

        /**
         * 最大延迟时间
         */
        @WithName("max-delay")
        @WithDefault("PT2S")
        Duration maxDelay();

        /**
         * 应重试的异常类型
         */
        @WithName("retry-on")
        @WithDefault("")
        String retryOn();

        /**
         * 不应重试的异常类型
         */
        @WithName("abort-on")
        @WithDefault("")
        String abortOn();
    }

    /**
     * 超时配置
     */
    interface TimeoutConfig {

        /**
         * 是否启用超时
         */
        @WithName("enabled")
        @WithDefault("true")
        boolean enabled();

        /**
         * 默认超时时间
         */
        @WithName("default-duration")
        @WithDefault("PT5S")
        Duration defaultDuration();

        /**
         * 是否启用超时指标收集
         */
        @WithName("metrics-enabled")
        @WithDefault("true")
        boolean metricsEnabled();
    }

    /**
     * 隔离舱配置
     */
    interface BulkheadConfig {

        /**
         * 是否启用隔离舱
         */
        @WithName("enabled")
        @WithDefault("true")
        boolean enabled();

        /**
         * 最大并发调用数
         */
        @WithName("max-concurrent-calls")
        @WithDefault("10")
        int maxConcurrentCalls();

        /**
         * 等待任务队列大小
         */
        @WithName("waiting-task-queue")
        @WithDefault("10")
        int waitingTaskQueue();

        /**
         * 等待超时
         */
        @WithName("wait-timeout")
        @WithDefault("PT0S")
        Duration waitTimeout();
    }

    /**
     * 降级配置
     */
    interface DegradationConfig {

        /**
         * 是否启用降级
         */
        @WithName("enabled")
        @WithDefault("true")
        boolean enabled();

        /**
         * 全局降级开关
         */
        @WithName("force-degraded")
        @WithDefault("false")
        boolean forceDegraded();

        /**
         * 降级模式下的超时时间
         */
        @WithName("degraded-timeout")
        @WithDefault("PT1S")
        Duration degradedTimeout();
    }
}
