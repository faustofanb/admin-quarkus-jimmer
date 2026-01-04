package io.github.faustofan.admin.shared.avaliable.constants;

/**
 * 熔断器状态枚举
 */
public enum CircuitBreakerState {

    /**
     * 关闭状态
     * <p>
     * 正常工作状态，请求正常通过
     */
    CLOSED("关闭", "正常工作状态"),

    /**
     * 打开状态
     * <p>
     * 熔断状态，所有请求直接返回失败
     */
    OPEN("打开", "熔断状态，快速失败"),

    /**
     * 半开状态
     * <p>
     * 尝试恢复状态，允许部分请求通过测试
     */
    HALF_OPEN("半开", "尝试恢复状态");

    private final String name;
    private final String description;

    CircuitBreakerState(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /**
     * 获取状态名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取状态描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 是否允许请求通过
     */
    public boolean isRequestAllowed() {
        return this != OPEN;
    }

    /**
     * 是否处于熔断状态
     */
    public boolean isOpen() {
        return this == OPEN;
    }

    /**
     * 是否处于正常状态
     */
    public boolean isClosed() {
        return this == CLOSED;
    }

    /**
     * 是否处于半开状态
     */
    public boolean isHalfOpen() {
        return this == HALF_OPEN;
    }
}
