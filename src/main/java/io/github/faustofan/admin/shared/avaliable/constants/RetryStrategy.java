package io.github.faustofan.admin.shared.avaliable.constants;

/**
 * 重试策略枚举
 */
public enum RetryStrategy {

    /**
     * 固定延迟
     * <p>
     * 每次重试使用相同的延迟时间
     */
    FIXED("固定延迟", "每次重试间隔相同"),

    /**
     * 指数退避
     * <p>
     * 每次重试延迟时间指数增长
     */
    EXPONENTIAL("指数退避", "延迟时间指数增长"),

    /**
     * 斐波那契退避
     * <p>
     * 使用斐波那契数列作为延迟因子
     */
    FIBONACCI("斐波那契退避", "使用斐波那契数列"),

    /**
     * 随机延迟
     * <p>
     * 在指定范围内随机选择延迟时间
     */
    RANDOM("随机延迟", "随机延迟时间"),

    /**
     * 立即重试
     * <p>
     * 无延迟立即重试
     */
    IMMEDIATE("立即重试", "无延迟");

    private final String name;
    private final String description;

    RetryStrategy(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /**
     * 获取策略名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取策略描述
     */
    public String getDescription() {
        return description;
    }
}
