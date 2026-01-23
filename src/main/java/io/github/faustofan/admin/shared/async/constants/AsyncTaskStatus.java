package io.github.faustofan.admin.shared.async.constants;

/**
 * 异步任务状态枚举
 */
public enum AsyncTaskStatus {

    /**
     * 等待执行
     */
    PENDING("等待执行"),

    /**
     * 执行中
     */
    RUNNING("执行中"),

    /**
     * 执行成功
     */
    SUCCESS("执行成功"),

    /**
     * 执行失败
     */
    FAILED("执行失败"),

    /**
     * 已取消
     */
    CANCELLED("已取消"),

    /**
     * 执行超时
     */
    TIMEOUT("执行超时");

    private final String description;

    AsyncTaskStatus(String description) {
        this.description = description;
    }

    /**
     * 获取状态描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 判断任务是否已完成（成功、失败、取消、超时）
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == CANCELLED || this == TIMEOUT;
    }

    /**
     * 判断任务是否可取消（等待中或执行中）
     */
    public boolean isCancellable() {
        return this == PENDING || this == RUNNING;
    }
}
