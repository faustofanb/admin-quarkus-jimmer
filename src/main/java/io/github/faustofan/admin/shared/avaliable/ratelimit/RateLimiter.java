package io.github.faustofan.admin.shared.avaliable.ratelimit;

import java.time.Duration;
import java.util.Optional;

/**
 * 限流器接口
 * <p>
 * 提供限流能力的统一抽象
 */
public interface RateLimiter {

    /**
     * 尝试获取许可
     *
     * @param resourceName 资源名称
     * @return 如果获取成功返回 true，否则返回 false
     */
    boolean tryAcquire(String resourceName);

    /**
     * 尝试获取许可（自定义许可数）
     *
     * @param resourceName 资源名称
     * @param permits      请求的许可数
     * @return 如果获取成功返回 true，否则返回 false
     */
    boolean tryAcquire(String resourceName, int permits);

    /**
     * 尝试获取许可（等待一段时间）
     *
     * @param resourceName 资源名称
     * @param timeout      最大等待时间
     * @return 如果获取成功返回 true，否则返回 false
     */
    boolean tryAcquire(String resourceName, Duration timeout);

    /**
     * 获取许可（可能阻塞）
     *
     * @param resourceName 资源名称
     */
    void acquire(String resourceName);

    /**
     * 获取许可（可能阻塞，自定义许可数）
     *
     * @param resourceName 资源名称
     * @param permits      请求的许可数
     */
    void acquire(String resourceName, int permits);

    /**
     * 获取当前可用许可数
     *
     * @param resourceName 资源名称
     * @return 可用许可数，如果未配置则返回 empty
     */
    Optional<Long> getAvailablePermits(String resourceName);

    /**
     * 获取下次重置时间（毫秒）
     *
     * @param resourceName 资源名称
     * @return 距下次重置的毫秒数
     */
    Optional<Long> getResetTimeMs(String resourceName);

    /**
     * 配置限流规则
     *
     * @param resourceName   资源名称
     * @param permitsPerPeriod 每时间段许可数
     * @param period         时间段
     */
    void configure(String resourceName, int permitsPerPeriod, Duration period);

    /**
     * 移除限流规则
     *
     * @param resourceName 资源名称
     */
    void remove(String resourceName);

    /**
     * 重置限流器状态
     *
     * @param resourceName 资源名称
     */
    void reset(String resourceName);

    /**
     * 重置所有限流器状态
     */
    void resetAll();
}
