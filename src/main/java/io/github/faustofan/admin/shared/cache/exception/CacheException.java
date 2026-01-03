package io.github.faustofan.admin.shared.cache.exception;

/**
 * 缓存异常基类
 * <p>
 * 所有缓存相关的异常都应继承此类
 */
public class CacheException extends RuntimeException {

    private final CacheExceptionType type;
    private final String cacheKey;

    /**
     * 构造器 - 仅指定异常类型
     *
     * @param type 异常类型
     */
    public CacheException(CacheExceptionType type) {
        super(type.getMessage());
        this.type = type;
        this.cacheKey = null;
    }

    /**
     * 构造器 - 指定异常类型和关联的缓存Key
     *
     * @param type     异常类型
     * @param cacheKey 关联的缓存Key
     */
    public CacheException(CacheExceptionType type, String cacheKey) {
        super(type.getMessage() + " [key=" + cacheKey + "]");
        this.type = type;
        this.cacheKey = cacheKey;
    }

    /**
     * 构造器 - 指定异常类型、关联的缓存Key和原始异常
     *
     * @param type     异常类型
     * @param cacheKey 关联的缓存Key
     * @param cause    原始异常
     */
    public CacheException(CacheExceptionType type, String cacheKey, Throwable cause) {
        super(type.getMessage() + " [key=" + cacheKey + "]", cause);
        this.type = type;
        this.cacheKey = cacheKey;
    }

    /**
     * 构造器 - 指定异常类型和原始异常
     *
     * @param type  异常类型
     * @param cause 原始异常
     */
    public CacheException(CacheExceptionType type, Throwable cause) {
        super(type.getMessage(), cause);
        this.type = type;
        this.cacheKey = null;
    }

    /**
     * 构造器 - 指定异常类型和自定义消息
     *
     * @param type    异常类型
     * @param message 自定义消息
     */
    public CacheException(CacheExceptionType type, String cacheKey, String message) {
        super(message);
        this.type = type;
        this.cacheKey = cacheKey;
    }

    /**
     * 构造器 - 指定异常类型、自定义消息和原始异常
     *
     * @param type    异常类型
     * @param message 自定义消息
     * @param cause   原始异常
     */
    public CacheException(CacheExceptionType type, String cacheKey, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
        this.cacheKey = cacheKey;
    }

    /**
     * 获取异常类型
     */
    public CacheExceptionType getType() {
        return type;
    }

    /**
     * 获取错误码
     */
    public String getCode() {
        return type.getCode();
    }

    /**
     * 获取关联的缓存Key
     */
    public String getCacheKey() {
        return cacheKey;
    }

    // ===========================
    // 工厂方法
    // ===========================

    /**
     * 创建连接失败异常
     */
    public static CacheException connectionFailed(Throwable cause) {
        return new CacheException(CacheExceptionType.CONNECTION_FAILED, cause);
    }

    /**
     * 创建读取失败异常
     */
    public static CacheException readFailed(String key, Throwable cause) {
        return new CacheException(CacheExceptionType.READ_FAILED, key, cause);
    }

    /**
     * 创建写入失败异常
     */
    public static CacheException writeFailed(String key, Throwable cause) {
        return new CacheException(CacheExceptionType.WRITE_FAILED, key, cause);
    }

    /**
     * 创建删除失败异常
     */
    public static CacheException deleteFailed(String key, Throwable cause) {
        return new CacheException(CacheExceptionType.DELETE_FAILED, key, cause);
    }

    /**
     * 创建序列化失败异常
     */
    public static CacheException serializationFailed(String key, Throwable cause) {
        return new CacheException(CacheExceptionType.SERIALIZATION_FAILED, key, cause);
    }

    /**
     * 创建反序列化失败异常
     */
    public static CacheException deserializationFailed(String key, Throwable cause) {
        return new CacheException(CacheExceptionType.DESERIALIZATION_FAILED, key, cause);
    }

    /**
     * 创建操作超时异常
     */
    public static CacheException timeout(String key) {
        return new CacheException(CacheExceptionType.OPERATION_TIMEOUT, key);
    }

    /**
     * 创建布隆过滤器操作失败异常
     */
    public static CacheException bloomFilterFailed(String filterName, Throwable cause) {
        return new CacheException(CacheExceptionType.BLOOM_FILTER_FAILED, filterName, cause);
    }

    /**
     * 创建分布式锁获取失败异常
     */
    public static CacheException lockAcquireFailed(String lockKey) {
        return new CacheException(CacheExceptionType.LOCK_ACQUIRE_FAILED, lockKey);
    }

    /**
     * 创建分布式锁释放失败异常
     */
    public static CacheException lockReleaseFailed(String lockKey, Throwable cause) {
        return new CacheException(CacheExceptionType.LOCK_RELEASE_FAILED, lockKey, cause);
    }
}
