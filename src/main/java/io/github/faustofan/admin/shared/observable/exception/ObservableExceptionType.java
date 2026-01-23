package io.github.faustofan.admin.shared.observable.exception;

/**
 * 可观测性异常类型枚举
 */
public enum ObservableExceptionType {

    /**
     * 日志格式化失败
     */
    LOG_FORMAT_ERROR("OBS_001", "日志格式化失败"),

    /**
     * 指标记录失败
     */
    METRIC_RECORD_ERROR("OBS_002", "指标记录失败"),

    /**
     * 追踪初始化失败
     */
    TRACE_INIT_ERROR("OBS_003", "追踪初始化失败"),

    /**
     * 追踪上下文传播失败
     */
    TRACE_PROPAGATION_ERROR("OBS_004", "追踪上下文传播失败"),

    /**
     * MDC操作失败
     */
    MDC_ERROR("OBS_005", "MDC操作失败"),

    /**
     * 配置错误
     */
    CONFIG_ERROR("OBS_006", "配置错误"),

    /**
     * 序列化失败
     */
    SERIALIZATION_ERROR("OBS_007", "序列化失败"),

    /**
     * 未知错误
     */
    UNKNOWN("OBS_999", "未知错误");

    private final String code;
    private final String description;

    ObservableExceptionType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s", code, description);
    }
}
