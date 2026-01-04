package io.github.faustofan.admin.shared.avaliable.exception;

import io.github.faustofan.admin.shared.avaliable.constants.CircuitBreakerState;

/**
 * 熔断器打开异常
 * <p>
 * 当熔断器处于打开状态时抛出
 */
public class CircuitBreakerOpenException extends AvailabilityException {

    private static final String EXCEPTION_TYPE = "CIRCUIT_BREAKER_OPEN";
    private static final String DEFAULT_MESSAGE = "服务暂时不可用，请稍后重试";

    private final CircuitBreakerState state;
    private final long remainingDelayMs;

    public CircuitBreakerOpenException() {
        super(DEFAULT_MESSAGE);
        this.state = CircuitBreakerState.OPEN;
        this.remainingDelayMs = 0;
    }

    public CircuitBreakerOpenException(String resourceName) {
        super(DEFAULT_MESSAGE, resourceName);
        this.state = CircuitBreakerState.OPEN;
        this.remainingDelayMs = 0;
    }

    public CircuitBreakerOpenException(String resourceName, CircuitBreakerState state) {
        super(DEFAULT_MESSAGE, resourceName);
        this.state = state;
        this.remainingDelayMs = 0;
    }

    public CircuitBreakerOpenException(String resourceName, CircuitBreakerState state, long remainingDelayMs) {
        super(DEFAULT_MESSAGE, resourceName);
        this.state = state;
        this.remainingDelayMs = remainingDelayMs;
    }

    public CircuitBreakerOpenException(String resourceName, CircuitBreakerState state, long remainingDelayMs, String message) {
        super(message, resourceName);
        this.state = state;
        this.remainingDelayMs = remainingDelayMs;
    }

    /**
     * 获取熔断器当前状态
     */
    public CircuitBreakerState getState() {
        return state;
    }

    /**
     * 获取剩余延迟时间（毫秒）
     */
    public long getRemainingDelayMs() {
        return remainingDelayMs;
    }

    @Override
    public String getExceptionType() {
        return EXCEPTION_TYPE;
    }
}
