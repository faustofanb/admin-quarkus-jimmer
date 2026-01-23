package io.github.faustofan.admin.shared.distributed.constants;

/**
 * ID生成器类型枚举
 */
public enum IdGeneratorType {

    /**
     * 雪花算法
     * <p>
     * 分布式唯一ID生成，64位Long型
     * 组成：1位符号位 + 41位时间戳 + 5位数据中心ID + 5位机器ID + 12位序列号
     */
    SNOWFLAKE("雪花算法"),

    /**
     * UUID
     * <p>
     * 通用唯一标识符，128位，通常表示为32个十六进制字符
     */
    UUID("UUID"),

    /**
     * Redis自增
     * <p>
     * 基于Redis INCR命令的自增ID
     */
    REDIS_INCR("Redis自增"),

    /**
     * 数据库自增
     * <p>
     * 基于数据库序列或自增列
     */
    DATABASE("数据库自增");

    private final String description;

    IdGeneratorType(String description) {
        this.description = description;
    }

    /**
     * 获取类型描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 是否需要分布式协调
     */
    public boolean requiresDistributedCoordination() {
        return this == REDIS_INCR || this == DATABASE;
    }

    /**
     * 是否是本地生成
     */
    public boolean isLocalGeneration() {
        return this == SNOWFLAKE || this == UUID;
    }
}
