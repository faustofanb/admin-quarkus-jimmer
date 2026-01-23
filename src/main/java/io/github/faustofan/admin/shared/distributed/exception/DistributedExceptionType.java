package io.github.faustofan.admin.shared.distributed.exception;

/**
 * 分布式异常类型枚举
 */
public enum DistributedExceptionType {

    /**
     * 锁获取失败
     */
    LOCK_ACQUIRE_FAILED("DIST_001", "获取锁失败"),

    /**
     * 锁等待超时
     */
    LOCK_WAIT_TIMEOUT("DIST_002", "等待锁超时"),

    /**
     * 锁释放失败
     */
    LOCK_RELEASE_FAILED("DIST_003", "释放锁失败"),

    /**
     * 锁已被其他持有者持有
     */
    LOCK_HELD_BY_OTHERS("DIST_004", "锁已被其他持有者持有"),

    /**
     * 非法的锁操作
     */
    ILLEGAL_LOCK_OPERATION("DIST_005", "非法的锁操作"),

    /**
     * ID生成失败
     */
    ID_GENERATION_FAILED("DIST_006", "ID生成失败"),

    /**
     * 雪花算法时钟回拨
     */
    CLOCK_MOVED_BACKWARDS("DIST_007", "系统时钟回拨"),

    /**
     * 幂等检查失败 - 重复请求
     */
    IDEMPOTENT_DUPLICATE_REQUEST("DIST_008", "重复请求"),

    /**
     * 幂等Key无效
     */
    IDEMPOTENT_INVALID_KEY("DIST_009", "幂等Key无效"),

    /**
     * Redis连接失败
     */
    REDIS_CONNECTION_FAILED("DIST_010", "Redis连接失败"),

    /**
     * Redis命令执行失败
     */
    REDIS_COMMAND_FAILED("DIST_011", "Redis命令执行失败"),

    /**
     * 配置错误
     */
    CONFIGURATION_ERROR("DIST_012", "配置错误"),

    /**
     * 未知错误
     */
    UNKNOWN("DIST_999", "未知分布式错误");

    private final String code;
    private final String message;

    DistributedExceptionType(String code, String message) {
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
