package io.github.faustofan.admin.shared.cache.constants;

/**
 * 缓存操作类型枚举
 * <p>
 * 用于日志记录和监控
 */
public enum CacheOperationType {

    /**
     * 读取操作
     */
    GET("读取"),

    /**
     * 写入操作
     */
    PUT("写入"),

    /**
     * 删除操作
     */
    DELETE("删除"),

    /**
     * 批量删除
     */
    DELETE_BATCH("批量删除"),

    /**
     * 清空缓存
     */
    CLEAR("清空"),

    /**
     * 判断存在
     */
    EXISTS("存在检查"),

    /**
     * 刷新缓存
     */
    REFRESH("刷新"),

    /**
     * 本地缓存命中
     */
    L1_HIT("本地命中"),

    /**
     * Redis缓存命中
     */
    L2_HIT("Redis命中"),

    /**
     * 缓存未命中
     */
    MISS("未命中"),

    /**
     * 布隆过滤器检查
     */
    BLOOM_CHECK("布隆过滤器检查"),

    /**
     * 布隆过滤器添加
     */
    BLOOM_ADD("布隆过滤器添加");

    private final String description;

    CacheOperationType(String description) {
        this.description = description;
    }

    /**
     * 获取操作描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 是否是读取类操作
     */
    public boolean isReadOperation() {
        return this == GET || this == EXISTS || this == BLOOM_CHECK;
    }

    /**
     * 是否是写入类操作
     */
    public boolean isWriteOperation() {
        return this == PUT || this == DELETE || this == DELETE_BATCH || this == CLEAR || this == REFRESH || this == BLOOM_ADD;
    }

    /**
     * 是否是命中类操作
     */
    public boolean isHitOperation() {
        return this == L1_HIT || this == L2_HIT;
    }
}
