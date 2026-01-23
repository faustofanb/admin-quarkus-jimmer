package io.github.faustofan.admin.shared.observable.constants;

/**
 * 追踪状态枚举
 * <p>
 * 定义Span的执行状态
 */
public enum TraceStatus {

    /**
     * 未设置
     */
    UNSET("UNSET", "未设置", 0),

    /**
     * 正常完成
     */
    OK("OK", "正常", 1),

    /**
     * 发生错误
     */
    ERROR("ERROR", "错误", 2);

    private final String code;
    private final String description;
    private final int statusCode;

    TraceStatus(String code, String description, int statusCode) {
        this.code = code;
        this.description = description;
        this.statusCode = statusCode;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public int getStatusCode() {
        return statusCode;
    }

    /**
     * 根据HTTP状态码推断追踪状态
     *
     * @param httpStatus HTTP状态码
     * @return 追踪状态
     */
    public static TraceStatus fromHttpStatus(int httpStatus) {
        if (httpStatus >= 200 && httpStatus < 400) {
            return OK;
        } else if (httpStatus >= 400) {
            return ERROR;
        }
        return UNSET;
    }

    /**
     * 根据代码获取追踪状态
     *
     * @param code 代码
     * @return 追踪状态，找不到返回UNSET
     */
    public static TraceStatus fromCode(String code) {
        if (code == null) {
            return UNSET;
        }
        for (TraceStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        return UNSET;
    }
}
