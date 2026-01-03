package io.github.faustofan.admin.shared.observable.constants;

/**
 * 日志级别枚举
 * <p>
 * 定义可观测性日志的级别
 */
public enum LogLevel {

    /**
     * 追踪级别 - 最详细的日志
     */
    TRACE(0, "TRACE", "追踪"),

    /**
     * 调试级别
     */
    DEBUG(1, "DEBUG", "调试"),

    /**
     * 信息级别
     */
    INFO(2, "INFO", "信息"),

    /**
     * 警告级别
     */
    WARN(3, "WARN", "警告"),

    /**
     * 错误级别
     */
    ERROR(4, "ERROR", "错误"),

    /**
     * 严重级别 - 系统崩溃
     */
    FATAL(5, "FATAL", "严重");

    private final int priority;
    private final String code;
    private final String description;

    LogLevel(int priority, String code, String description) {
        this.priority = priority;
        this.code = code;
        this.description = description;
    }

    public int getPriority() {
        return priority;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 判断当前级别是否应该记录给定级别的日志
     *
     * @param level 目标级别
     * @return 是否应该记录
     */
    public boolean isEnabled(LogLevel level) {
        return this.priority <= level.priority;
    }

    /**
     * 根据代码获取日志级别
     *
     * @param code 代码
     * @return 日志级别，找不到返回INFO
     */
    public static LogLevel fromCode(String code) {
        if (code == null) {
            return INFO;
        }
        for (LogLevel level : values()) {
            if (level.code.equalsIgnoreCase(code)) {
                return level;
            }
        }
        return INFO;
    }
}
