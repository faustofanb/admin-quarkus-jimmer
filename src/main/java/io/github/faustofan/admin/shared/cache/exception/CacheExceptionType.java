package io.github.faustofan.admin.shared.cache.exception;

/**
 * 缓存异常类型枚举
 */
public enum CacheExceptionType {

    /**
     * 缓存连接失败
     */
    CONNECTION_FAILED("CACHE_001", "缓存连接失败"),

    /**
     * 缓存读取失败
     */
    READ_FAILED("CACHE_002", "缓存读取失败"),

    /**
     * 缓存写入失败
     */
    WRITE_FAILED("CACHE_003", "缓存写入失败"),

    /**
     * 缓存删除失败
     */
    DELETE_FAILED("CACHE_004", "缓存删除失败"),

    /**
     * 缓存序列化失败
     */
    SERIALIZATION_FAILED("CACHE_005", "缓存序列化失败"),

    /**
     * 缓存反序列化失败
     */
    DESERIALIZATION_FAILED("CACHE_006", "缓存反序列化失败"),

    /**
     * Redis命令执行失败
     */
    REDIS_COMMAND_FAILED("CACHE_007", "Redis命令执行失败"),

    /**
     * 布隆过滤器操作失败
     */
    BLOOM_FILTER_FAILED("CACHE_008", "布隆过滤器操作失败"),

    /**
     * 缓存Key不合法
     */
    INVALID_KEY("CACHE_009", "缓存Key不合法"),

    /**
     * 缓存值不合法
     */
    INVALID_VALUE("CACHE_010", "缓存值不合法"),

    /**
     * 缓存操作超时
     */
    OPERATION_TIMEOUT("CACHE_011", "缓存操作超时"),

    /**
     * 缓存穿透检测
     */
    CACHE_PENETRATION("CACHE_012", "缓存穿透检测"),

    /**
     * 分布式锁获取失败
     */
    LOCK_ACQUIRE_FAILED("CACHE_013", "分布式锁获取失败"),

    /**
     * 分布式锁释放失败
     */
    LOCK_RELEASE_FAILED("CACHE_014", "分布式锁释放失败"),

    /**
     * 未知错误
     */
    UNKNOWN("CACHE_999", "未知缓存错误");

    private final String code;
    private final String message;

    CacheExceptionType(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 获取错误码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取错误消息
     */
    public String getMessage() {
        return message;
    }

    /**
     * 格式化错误消息
     *
     * @param args 动态参数
     * @return 格式化后的消息
     */
    public String format(Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }
        return String.format(message + ": %s", args);
    }
}
