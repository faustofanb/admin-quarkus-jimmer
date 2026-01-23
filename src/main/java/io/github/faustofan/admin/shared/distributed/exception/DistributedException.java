package io.github.faustofan.admin.shared.distributed.exception;

/**
 * 分布式异常基类
 * <p>
 * 所有分布式相关的异常都应继承此类
 */
public class DistributedException extends RuntimeException {

    private final DistributedExceptionType type;
    private final String resourceKey;

    /**
     * 构造器 - 仅指定异常类型
     *
     * @param type 异常类型
     */
    public DistributedException(DistributedExceptionType type) {
        super(type.getMessage());
        this.type = type;
        this.resourceKey = null;
    }

    /**
     * 构造器 - 指定异常类型和关联的资源Key
     *
     * @param type        异常类型
     * @param resourceKey 关联的资源Key（锁Key、幂等Key等）
     */
    public DistributedException(DistributedExceptionType type, String resourceKey) {
        super(type.getMessage() + " [key=" + resourceKey + "]");
        this.type = type;
        this.resourceKey = resourceKey;
    }

    /**
     * 构造器 - 指定异常类型、关联的资源Key和原始异常
     *
     * @param type        异常类型
     * @param resourceKey 关联的资源Key
     * @param cause       原始异常
     */
    public DistributedException(DistributedExceptionType type, String resourceKey, Throwable cause) {
        super(type.getMessage() + " [key=" + resourceKey + "]", cause);
        this.type = type;
        this.resourceKey = resourceKey;
    }

    /**
     * 构造器 - 指定异常类型和原始异常
     *
     * @param type  异常类型
     * @param cause 原始异常
     */
    public DistributedException(DistributedExceptionType type, Throwable cause) {
        super(type.getMessage(), cause);
        this.type = type;
        this.resourceKey = null;
    }

    /**
     * 获取异常类型
     */
    public DistributedExceptionType getType() {
        return type;
    }

    /**
     * 获取错误码
     */
    public String getCode() {
        return type.getCode();
    }

    /**
     * 获取关联的资源Key
     */
    public String getResourceKey() {
        return resourceKey;
    }

    // ===========================
    // 工厂方法
    // ===========================

    /**
     * 创建锁获取失败异常
     */
    public static DistributedException lockAcquireFailed(String lockKey) {
        return new DistributedException(DistributedExceptionType.LOCK_ACQUIRE_FAILED, lockKey);
    }

    /**
     * 创建锁等待超时异常
     */
    public static DistributedException lockWaitTimeout(String lockKey) {
        return new DistributedException(DistributedExceptionType.LOCK_WAIT_TIMEOUT, lockKey);
    }

    /**
     * 创建锁释放失败异常
     */
    public static DistributedException lockReleaseFailed(String lockKey, Throwable cause) {
        return new DistributedException(DistributedExceptionType.LOCK_RELEASE_FAILED, lockKey, cause);
    }

    /**
     * 创建ID生成失败异常
     */
    public static DistributedException idGenerationFailed(Throwable cause) {
        return new DistributedException(DistributedExceptionType.ID_GENERATION_FAILED, cause);
    }

    /**
     * 创建时钟回拨异常
     */
    public static DistributedException clockMovedBackwards(long lastTimestamp, long currentTimestamp) {
        return new DistributedException(
                DistributedExceptionType.CLOCK_MOVED_BACKWARDS,
                "last=" + lastTimestamp + ", current=" + currentTimestamp
        );
    }

    /**
     * 创建重复请求异常
     */
    public static DistributedException duplicateRequest(String idempotentKey) {
        return new DistributedException(DistributedExceptionType.IDEMPOTENT_DUPLICATE_REQUEST, idempotentKey);
    }

    /**
     * 创建幂等Key无效异常
     */
    public static DistributedException invalidIdempotentKey(String idempotentKey) {
        return new DistributedException(DistributedExceptionType.IDEMPOTENT_INVALID_KEY, idempotentKey);
    }

    /**
     * 创建Redis连接失败异常
     */
    public static DistributedException redisConnectionFailed(Throwable cause) {
        return new DistributedException(DistributedExceptionType.REDIS_CONNECTION_FAILED, cause);
    }

    /**
     * 创建配置错误异常
     */
    public static DistributedException configurationError(String message) {
        return new DistributedException(DistributedExceptionType.CONFIGURATION_ERROR, message);
    }
}
