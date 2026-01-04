package io.github.faustofan.admin.shared.avaliable.exception;

/**
 * 限流异常
 * <p>
 * 当请求被限流器拒绝时抛出
 */
public class RateLimitExceededException extends AvailabilityException {

    private static final String EXCEPTION_TYPE = "RATE_LIMIT_EXCEEDED";
    private static final String DEFAULT_MESSAGE = "请求过于频繁，请稍后重试";

    private final long retryAfterMs;

    public RateLimitExceededException() {
        super(DEFAULT_MESSAGE);
        this.retryAfterMs = 0;
    }

    public RateLimitExceededException(String resourceName) {
        super(DEFAULT_MESSAGE, resourceName);
        this.retryAfterMs = 0;
    }

    public RateLimitExceededException(String resourceName, long retryAfterMs) {
        super(DEFAULT_MESSAGE, resourceName);
        this.retryAfterMs = retryAfterMs;
    }

    public RateLimitExceededException(String resourceName, long retryAfterMs, String message) {
        super(message, resourceName);
        this.retryAfterMs = retryAfterMs;
    }

    /**
     * 获取建议的重试等待时间（毫秒）
     */
    public long getRetryAfterMs() {
        return retryAfterMs;
    }

    @Override
    public String getExceptionType() {
        return EXCEPTION_TYPE;
    }
}
