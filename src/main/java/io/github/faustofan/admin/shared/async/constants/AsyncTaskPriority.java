package io.github.faustofan.admin.shared.async.constants;

/**
 * 异步任务优先级枚举
 * <p>
 * 用于区分不同重要程度的异步任务
 */
public enum AsyncTaskPriority {

    /**
     * 最高优先级 - 系统关键任务
     */
    CRITICAL(0, "关键任务"),

    /**
     * 高优先级 - 重要业务任务
     */
    HIGH(1, "高优先级"),

    /**
     * 正常优先级 - 普通任务
     */
    NORMAL(2, "正常优先级"),

    /**
     * 低优先级 - 后台任务
     */
    LOW(3, "低优先级"),

    /**
     * 最低优先级 - 可延迟任务
     */
    IDLE(4, "空闲任务");

    private final int level;
    private final String description;

    AsyncTaskPriority(int level, String description) {
        this.level = level;
        this.description = description;
    }

    /**
     * 获取优先级等级（数字越小优先级越高）
     */
    public int getLevel() {
        return level;
    }

    /**
     * 获取优先级描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 比较优先级
     *
     * @param other 另一个优先级
     * @return 如果当前优先级高于other返回true
     */
    public boolean isHigherThan(AsyncTaskPriority other) {
        return this.level < other.level;
    }
}
