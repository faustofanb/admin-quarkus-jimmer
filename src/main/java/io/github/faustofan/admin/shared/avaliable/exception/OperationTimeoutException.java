package io.github.faustofan.admin.shared.avaliable.exception;

import java.time.Duration;

/**
 * 超时异常
 * <p>
 * 当操作超过配置的超时时间时抛出
 */
public class OperationTimeoutException extends AvailabilityException {

    private static final String EXCEPTION_TYPE = "OPERATION_TIMEOUT";
    private static final String DEFAULT_MESSAGE = "操作超时，请稍后重试";

    private final Duration timeout;
    private final long elapsedMs;

    public OperationTimeoutException() {
        super(DEFAULT_MESSAGE);
        this.timeout = Duration.ZERO;
        this.elapsedMs = 0;
    }

    public OperationTimeoutException(String resourceName) {
        super(DEFAULT_MESSAGE, resourceName);
        this.timeout = Duration.ZERO;
        this.elapsedMs = 0;
    }

    public OperationTimeoutException(String resourceName, Duration timeout) {
        super(DEFAULT_MESSAGE, resourceName);
        this.timeout = timeout;
        this.elapsedMs = timeout.toMillis();
    }

    public OperationTimeoutException(String resourceName, Duration timeout, long elapsedMs) {
        super(DEFAULT_MESSAGE, resourceName);
        this.timeout = timeout;
        this.elapsedMs = elapsedMs;
    }

    public OperationTimeoutException(String resourceName, Duration timeout, long elapsedMs, Throwable cause) {
        super(DEFAULT_MESSAGE, resourceName, cause);
        this.timeout = timeout;
        this.elapsedMs = elapsedMs;
    }

    /**
     * 获取配置的超时时间
     */
    public Duration getTimeout() {
        return timeout;
    }

    /**
     * 获取实际耗时（毫秒）
     */
    public long getElapsedMs() {
        return elapsedMs;
    }

    @Override
    public String getExceptionType() {
        return EXCEPTION_TYPE;
    }
}
