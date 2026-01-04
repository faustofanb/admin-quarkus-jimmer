package io.github.faustofan.admin.shared.avaliable.circuit;

import io.github.faustofan.admin.shared.avaliable.constants.CircuitBreakerState;

import java.time.Instant;
import java.util.Optional;

/**
 * 熔断器上下文
 * <p>
 * 封装熔断器的当前状态和统计信息
 */
public class CircuitBreakerContext {

    private final String name;
    private volatile CircuitBreakerState state;
    private volatile Instant stateChangedAt;
    private volatile long successCount;
    private volatile long failureCount;
    private volatile long rejectedCount;
    private volatile Instant lastFailureAt;
    private volatile Instant openedAt;

    public CircuitBreakerContext(String name) {
        this.name = name;
        this.state = CircuitBreakerState.CLOSED;
        this.stateChangedAt = Instant.now();
        this.successCount = 0;
        this.failureCount = 0;
        this.rejectedCount = 0;
    }

    /**
     * 获取熔断器名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取当前状态
     */
    public CircuitBreakerState getState() {
        return state;
    }

    /**
     * 设置状态
     */
    public void setState(CircuitBreakerState newState) {
        if (this.state != newState) {
            this.state = newState;
            this.stateChangedAt = Instant.now();

            if (newState == CircuitBreakerState.OPEN) {
                this.openedAt = Instant.now();
            }
        }
    }

    /**
     * 获取状态变更时间
     */
    public Instant getStateChangedAt() {
        return stateChangedAt;
    }

    /**
     * 获取成功计数
     */
    public long getSuccessCount() {
        return successCount;
    }

    /**
     * 增加成功计数
     */
    public void incrementSuccess() {
        this.successCount++;
    }

    /**
     * 获取失败计数
     */
    public long getFailureCount() {
        return failureCount;
    }

    /**
     * 增加失败计数
     */
    public void incrementFailure() {
        this.failureCount++;
        this.lastFailureAt = Instant.now();
    }

    /**
     * 获取拒绝计数
     */
    public long getRejectedCount() {
        return rejectedCount;
    }

    /**
     * 增加拒绝计数
     */
    public void incrementRejected() {
        this.rejectedCount++;
    }

    /**
     * 获取最后失败时间
     */
    public Optional<Instant> getLastFailureAt() {
        return Optional.ofNullable(lastFailureAt);
    }

    /**
     * 获取熔断器打开时间
     */
    public Optional<Instant> getOpenedAt() {
        return Optional.ofNullable(openedAt);
    }

    /**
     * 计算失败率
     */
    public double getFailureRatio() {
        long total = successCount + failureCount;
        if (total == 0) {
            return 0.0;
        }
        return (double) failureCount / total;
    }

    /**
     * 获取总请求数
     */
    public long getTotalCount() {
        return successCount + failureCount;
    }

    /**
     * 重置统计信息
     */
    public void resetStats() {
        this.successCount = 0;
        this.failureCount = 0;
    }

    /**
     * 完全重置熔断器
     */
    public void reset() {
        this.state = CircuitBreakerState.CLOSED;
        this.stateChangedAt = Instant.now();
        this.successCount = 0;
        this.failureCount = 0;
        this.rejectedCount = 0;
        this.lastFailureAt = null;
        this.openedAt = null;
    }

    /**
     * 计算处于当前状态的持续时间（毫秒）
     */
    public long getStateDurationMs() {
        return Instant.now().toEpochMilli() - stateChangedAt.toEpochMilli();
    }

    @Override
    public String toString() {
        return String.format("CircuitBreaker[%s]{state=%s, success=%d, failure=%d, ratio=%.2f}",
                name, state.getName(), successCount, failureCount, getFailureRatio());
    }
}
