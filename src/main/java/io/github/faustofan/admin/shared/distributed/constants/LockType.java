package io.github.faustofan.admin.shared.distributed.constants;

/**
 * 锁类型枚举
 */
public enum LockType {

    /**
     * 本地JVM锁
     * <p>
     * 基于 ReentrantLock 实现，仅适用于单机环境
     */
    LOCAL("本地锁"),

    /**
     * Redis分布式锁
     * <p>
     * 基于 Redis SET NX 实现，适用于分布式环境
     */
    REDIS("Redis分布式锁"),

    /**
     * 自动选择
     * <p>
     * 根据配置自动选择合适的锁类型
     */
    AUTO("自动选择");

    private final String description;

    LockType(String description) {
        this.description = description;
    }

    /**
     * 获取类型描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 是否是分布式锁
     */
    public boolean isDistributed() {
        return this == REDIS;
    }

    /**
     * 是否是本地锁
     */
    public boolean isLocal() {
        return this == LOCAL;
    }
}
