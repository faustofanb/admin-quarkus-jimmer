package io.github.faustofan.admin.shared.cache.constants;

/**
 * 缓存策略枚举
 * <p>
 * 定义不同的缓存读写策略
 */
public enum CacheStrategy {

    /**
     * 仅使用本地缓存（L1）
     * <p>
     * 适用于：
     * <ul>
     *   <li>数据更新不频繁</li>
     *   <li>允许短时间数据不一致</li>
     *   <li>对读取性能要求极高</li>
     * </ul>
     */
    LOCAL_ONLY("仅本地缓存"),

    /**
     * 仅使用Redis缓存（L2）
     * <p>
     * 适用于：
     * <ul>
     *   <li>分布式环境需要数据一致性</li>
     *   <li>缓存数据量较大</li>
     *   <li>需要跨服务共享缓存</li>
     * </ul>
     */
    REDIS_ONLY("仅Redis缓存"),

    /**
     * 二级缓存：先查本地，再查Redis（L1 -> L2）
     * <p>
     * 适用于：
     * <ul>
     *   <li>热点数据访问</li>
     *   <li>需要兼顾性能和一致性</li>
     *   <li>读多写少的场景</li>
     * </ul>
     */
    TWO_LEVEL("二级缓存"),

    /**
     * 读写穿透策略
     * <p>
     * 读取时：缓存未命中自动从数据源加载并写入缓存
     * 写入时：同时更新数据源和缓存
     */
    READ_WRITE_THROUGH("读写穿透"),

    /**
     * 写回策略（Write-Behind）
     * <p>
     * 写入时先更新缓存，异步批量写入数据源
     */
    WRITE_BEHIND("写回策略");

    private final String description;

    CacheStrategy(String description) {
        this.description = description;
    }

    /**
     * 获取策略描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 是否包含本地缓存
     */
    public boolean usesLocalCache() {
        return this == LOCAL_ONLY || this == TWO_LEVEL;
    }

    /**
     * 是否包含Redis缓存
     */
    public boolean usesRedisCache() {
        return this == REDIS_ONLY || this == TWO_LEVEL || this == READ_WRITE_THROUGH || this == WRITE_BEHIND;
    }

    /**
     * 是否是二级缓存
     */
    public boolean isTwoLevel() {
        return this == TWO_LEVEL;
    }
}
