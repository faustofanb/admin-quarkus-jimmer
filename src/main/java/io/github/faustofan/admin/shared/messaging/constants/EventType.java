package io.github.faustofan.admin.shared.messaging.constants;

/**
 * 事件类型枚举
 * <p>
 * 定义系统中所有事件的类型
 */
public enum EventType {

    // ===========================
    // 领域事件
    // ===========================

    /**
     * 实体创建
     */
    CREATED("created", "实体创建"),

    /**
     * 实体更新
     */
    UPDATED("updated", "实体更新"),

    /**
     * 实体删除
     */
    DELETED("deleted", "实体删除"),

    /**
     * 实体状态变更
     */
    STATUS_CHANGED("status_changed", "状态变更"),

    // ===========================
    // 系统事件
    // ===========================

    /**
     * 缓存失效
     */
    CACHE_INVALIDATED("cache_invalidated", "缓存失效"),

    /**
     * 配置变更
     */
    CONFIG_CHANGED("config_changed", "配置变更"),

    /**
     * 重新加载
     */
    RELOAD("reload", "重新加载"),

    // ===========================
    // 用户事件
    // ===========================

    /**
     * 用户登录
     */
    USER_LOGGED_IN("user_logged_in", "用户登录"),

    /**
     * 用户登出
     */
    USER_LOGGED_OUT("user_logged_out", "用户登出"),

    /**
     * 用户注册
     */
    USER_REGISTERED("user_registered", "用户注册"),

    /**
     * 密码变更
     */
    PASSWORD_CHANGED("password_changed", "密码变更"),

    // ===========================
    // 审计事件
    // ===========================

    /**
     * 操作日志
     */
    OPERATION_LOGGED("operation_logged", "操作日志"),

    /**
     * 访问日志
     */
    ACCESS_LOGGED("access_logged", "访问日志"),

    // ===========================
    // 通用事件
    // ===========================

    /**
     * 自定义事件
     */
    CUSTOM("custom", "自定义事件");

    private final String code;
    private final String description;

    EventType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据code查找EventType
     */
    public static EventType fromCode(String code) {
        for (EventType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return CUSTOM;
    }
}
