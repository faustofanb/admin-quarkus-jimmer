package io.github.faustofan.admin.shared.observable.constants;

/**
 * 指标类型枚举
 * <p>
 * 定义可用的度量类型
 */
public enum MetricType {

    /**
     * 计数器 - 只增不减的累加值
     * <p>
     * 例如：请求总数、错误总数
     */
    COUNTER("counter", "计数器", "Monotonically increasing value"),

    /**
     * 仪表盘 - 可增可减的瞬时值
     * <p>
     * 例如：当前连接数、队列长度
     */
    GAUGE("gauge", "仪表盘", "Current value that can go up or down"),

    /**
     * 直方图 - 值的分布统计
     * <p>
     * 例如：请求延迟分布
     */
    HISTOGRAM("histogram", "直方图", "Distribution of values"),

    /**
     * 计时器 - 时间度量
     * <p>
     * 例如：请求处理时间
     */
    TIMER("timer", "计时器", "Time-based measurement"),

    /**
     * 摘要 - 百分位数统计
     * <p>
     * 例如：响应时间百分位
     */
    SUMMARY("summary", "摘要", "Summary with percentiles"),

    /**
     * 分布摘要 - 值分布统计（非时间）
     * <p>
     * 例如：请求/响应大小分布
     */
    DISTRIBUTION_SUMMARY("distribution_summary", "分布摘要", "Distribution of non-time values");

    private final String code;
    private final String description;
    private final String englishDescription;

    MetricType(String code, String description, String englishDescription) {
        this.code = code;
        this.description = description;
        this.englishDescription = englishDescription;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public String getEnglishDescription() {
        return englishDescription;
    }

    /**
     * 根据代码获取指标类型
     *
     * @param code 代码
     * @return 指标类型，找不到返回COUNTER
     */
    public static MetricType fromCode(String code) {
        if (code == null) {
            return COUNTER;
        }
        for (MetricType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return COUNTER;
    }
}
