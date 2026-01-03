package io.github.faustofan.admin.shared.observable.constants;

/**
 * 日志类别枚举
 * <p>
 * 定义不同类别的日志，用于分类和筛选
 */
public enum LogCategory {

    /**
     * 业务日志 - 业务逻辑相关
     */
    BUSINESS("BUSINESS", "业务日志", "📋"),

    /**
     * HTTP日志 - HTTP请求/响应相关
     */
    HTTP("HTTP", "HTTP日志", "🌐"),

    /**
     * SQL日志 - 数据库操作相关
     */
    SQL("SQL", "SQL日志", "🗄️"),

    /**
     * 安全日志 - 认证/授权相关
     */
    SECURITY("SECURITY", "安全日志", "🔒"),

    /**
     * 审计日志 - 操作审计相关
     */
    AUDIT("AUDIT", "审计日志", "📝"),

    /**
     * 性能日志 - 性能监控相关
     */
    PERFORMANCE("PERFORMANCE", "性能日志", "⚡"),

    /**
     * 系统日志 - 系统运行时信息
     */
    SYSTEM("SYSTEM", "系统日志", "🖥️"),

    /**
     * 缓存日志 - 缓存操作相关
     */
    CACHE("CACHE", "缓存日志", "💾"),

    /**
     * 消息日志 - 消息队列相关
     */
    MESSAGING("MESSAGING", "消息日志", "📨"),

    /**
     * 调度日志 - 定时任务相关
     */
    SCHEDULER("SCHEDULER", "调度日志", "⏰"),

    /**
     * 外部调用日志 - 第三方服务调用
     */
    EXTERNAL("EXTERNAL", "外部调用日志", "🔗");

    private final String code;
    private final String description;
    private final String icon;

    LogCategory(String code, String description, String icon) {
        this.code = code;
        this.description = description;
        this.icon = icon;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public String getIcon() {
        return icon;
    }

    /**
     * 获取带图标的描述
     */
    public String getIconDescription() {
        return icon + " " + description;
    }

    /**
     * 根据代码获取日志类别
     *
     * @param code 代码
     * @return 日志类别，找不到返回BUSINESS
     */
    public static LogCategory fromCode(String code) {
        if (code == null) {
            return BUSINESS;
        }
        for (LogCategory category : values()) {
            if (category.code.equalsIgnoreCase(code)) {
                return category;
            }
        }
        return BUSINESS;
    }
}
