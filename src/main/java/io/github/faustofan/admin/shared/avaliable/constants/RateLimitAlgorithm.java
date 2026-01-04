package io.github.faustofan.admin.shared.avaliable.constants;

/**
 * 限流算法枚举
 */
public enum RateLimitAlgorithm {

    /**
     * 固定窗口计数器
     * <p>
     * 在固定时间窗口内计数，简单但可能有边界突发问题
     */
    FIXED_WINDOW("固定窗口", "固定时间窗口内计数"),

    /**
     * 滑动窗口计数器
     * <p>
     * 平滑处理边界问题，更精确的限流
     */
    SLIDING_WINDOW("滑动窗口", "滑动时间窗口计数"),

    /**
     * 令牌桶算法
     * <p>
     * 以固定速率添加令牌，允许一定程度的突发流量
     */
    TOKEN_BUCKET("令牌桶", "固定速率添加令牌"),

    /**
     * 漏桶算法
     * <p>
     * 以固定速率处理请求，平滑流量
     */
    LEAKY_BUCKET("漏桶", "固定速率处理请求"),

    /**
     * 并发限流
     * <p>
     * 限制并发执行数量
     */
    CONCURRENT("并发限流", "限制并发执行数量");

    private final String name;
    private final String description;

    RateLimitAlgorithm(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /**
     * 获取算法名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取算法描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 是否支持分布式
     */
    public boolean supportsDistributed() {
        return this == FIXED_WINDOW || this == SLIDING_WINDOW || this == TOKEN_BUCKET;
    }
}
