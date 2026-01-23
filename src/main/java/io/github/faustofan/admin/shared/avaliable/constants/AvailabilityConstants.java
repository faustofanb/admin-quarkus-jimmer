package io.github.faustofan.admin.shared.avaliable.constants;

import java.time.Duration;

/**
 * 可用性基础设施常量定义
 * <p>
 * 集中管理所有可用性相关的常量，避免魔法字符串
 */
public final class AvailabilityConstants {

    private AvailabilityConstants() {
        // 禁止实例化
    }

    // ===========================
    // 限流默认配置
    // ===========================

    /**
     * 默认限流窗口时间（秒）
     */
    public static final long DEFAULT_RATE_LIMIT_WINDOW_SECONDS = 1L;

    /**
     * 默认限流请求数
     */
    public static final long DEFAULT_RATE_LIMIT_PERMITS = 100L;

    /**
     * 默认限流窗口时间
     */
    public static final Duration DEFAULT_RATE_LIMIT_WINDOW = Duration.ofSeconds(DEFAULT_RATE_LIMIT_WINDOW_SECONDS);

    /**
     * 默认限流最小间隔
     */
    public static final Duration DEFAULT_MIN_SPACING = Duration.ZERO;

    // ===========================
    // 熔断器默认配置
    // ===========================

    /**
     * 默认熔断器失败率阈值（百分比）
     */
    public static final double DEFAULT_FAILURE_RATIO = 0.5;

    /**
     * 默认请求量阈值（用于计算失败率）
     */
    public static final int DEFAULT_REQUEST_VOLUME_THRESHOLD = 20;

    /**
     * 默认熔断器延迟时间（毫秒）
     */
    public static final long DEFAULT_DELAY_MS = 5000L;

    /**
     * 默认熔断器延迟时间
     */
    public static final Duration DEFAULT_DELAY = Duration.ofMillis(DEFAULT_DELAY_MS);

    /**
     * 默认成功阈值（半开状态需要成功的请求数）
     */
    public static final int DEFAULT_SUCCESS_THRESHOLD = 5;

    // ===========================
    // 重试默认配置
    // ===========================

    /**
     * 默认最大重试次数
     */
    public static final int DEFAULT_MAX_RETRIES = 3;

    /**
     * 默认重试延迟（毫秒）
     */
    public static final long DEFAULT_RETRY_DELAY_MS = 200L;

    /**
     * 默认重试延迟
     */
    public static final Duration DEFAULT_RETRY_DELAY = Duration.ofMillis(DEFAULT_RETRY_DELAY_MS);

    /**
     * 默认重试延迟抖动（毫秒）
     */
    public static final long DEFAULT_JITTER_MS = 50L;

    /**
     * 默认重试延迟抖动
     */
    public static final Duration DEFAULT_JITTER = Duration.ofMillis(DEFAULT_JITTER_MS);

    /**
     * 默认最大重试延迟（毫秒）
     */
    public static final long DEFAULT_MAX_DELAY_MS = 2000L;

    /**
     * 默认最大重试延迟
     */
    public static final Duration DEFAULT_MAX_DELAY = Duration.ofMillis(DEFAULT_MAX_DELAY_MS);

    // ===========================
    // 超时默认配置
    // ===========================

    /**
     * 默认超时时间（毫秒）
     */
    public static final long DEFAULT_TIMEOUT_MS = 5000L;

    /**
     * 默认超时时间
     */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofMillis(DEFAULT_TIMEOUT_MS);

    // ===========================
    // 隔离舱默认配置
    // ===========================

    /**
     * 默认最大并发执行数
     */
    public static final int DEFAULT_MAX_CONCURRENT_CALLS = 10;

    /**
     * 默认等待队列大小
     */
    public static final int DEFAULT_WAITING_TASK_QUEUE = 10;

    // ===========================
    // Redis Key前缀
    // ===========================

    /**
     * 可用性Key命名空间
     */
    public static final class KeyPrefix {
        private KeyPrefix() {}

        /**
         * 全局Key前缀
         */
        public static final String GLOBAL = "admin:";

        /**
         * 限流Key前缀
         */
        public static final String RATE_LIMIT = GLOBAL + "ratelimit:";

        /**
         * 熔断器Key前缀
         */
        public static final String CIRCUIT_BREAKER = GLOBAL + "circuit:";

        /**
         * 分布式限流计数器前缀
         */
        public static final String RATE_LIMIT_COUNTER = RATE_LIMIT + "counter:";

        /**
         * 熔断器状态前缀
         */
        public static final String CIRCUIT_STATE = CIRCUIT_BREAKER + "state:";
    }

    // ===========================
    // 指标名称
    // ===========================

    /**
     * 指标名称常量
     */
    public static final class MetricName {
        private MetricName() {}

        /**
         * 限流拒绝计数
         */
        public static final String RATE_LIMIT_REJECTED = "availability.ratelimit.rejected";

        /**
         * 限流通过计数
         */
        public static final String RATE_LIMIT_PERMITTED = "availability.ratelimit.permitted";

        /**
         * 熔断器打开计数
         */
        public static final String CIRCUIT_BREAKER_OPENED = "availability.circuit.opened";

        /**
         * 熔断器关闭计数
         */
        public static final String CIRCUIT_BREAKER_CLOSED = "availability.circuit.closed";

        /**
         * 熔断器半开计数
         */
        public static final String CIRCUIT_BREAKER_HALF_OPEN = "availability.circuit.half_open";

        /**
         * 回退触发计数
         */
        public static final String FALLBACK_TRIGGERED = "availability.fallback.triggered";

        /**
         * 重试计数
         */
        public static final String RETRY_COUNT = "availability.retry.count";

        /**
         * 超时计数
         */
        public static final String TIMEOUT_COUNT = "availability.timeout.count";

        /**
         * 隔离舱拒绝计数
         */
        public static final String BULKHEAD_REJECTED = "availability.bulkhead.rejected";
    }

    // ===========================
    // 日志前缀
    // ===========================

    /**
     * 日志前缀常量
     */
    public static final class LogPrefix {
        private LogPrefix() {}

        public static final String RATE_LIMIT = "[RateLimit]";
        public static final String CIRCUIT_BREAKER = "[CircuitBreaker]";
        public static final String FALLBACK = "[Fallback]";
        public static final String RETRY = "[Retry]";
        public static final String TIMEOUT = "[Timeout]";
        public static final String BULKHEAD = "[Bulkhead]";
    }
}
