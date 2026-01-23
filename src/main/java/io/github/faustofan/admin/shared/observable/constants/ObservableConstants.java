package io.github.faustofan.admin.shared.observable.constants;

import java.time.Duration;

/**
 * 可观测性基础设施常量定义
 * <p>
 * 集中管理所有可观测性相关的常量，避免魔法字符串
 */
public final class ObservableConstants {

    private ObservableConstants() {
        // 禁止实例化
    }

    // ===========================
    // 日志配置
    // ===========================

    /**
     * 默认日志缓冲区大小
     */
    public static final int DEFAULT_LOG_BUFFER_SIZE = 8192;

    /**
     * 默认日志刷新间隔（毫秒）
     */
    public static final long DEFAULT_LOG_FLUSH_INTERVAL_MS = 1000L;

    /**
     * 最大日志消息长度
     */
    public static final int MAX_LOG_MESSAGE_LENGTH = 10_000;

    /**
     * SQL日志最大长度
     */
    public static final int MAX_SQL_LOG_LENGTH = 5_000;

    /**
     * HTTP请求体/响应体最大日志长度
     */
    public static final int MAX_HTTP_BODY_LOG_LENGTH = 2_000;

    // ===========================
    // 指标配置
    // ===========================

    /**
     * 默认指标采样率
     */
    public static final double DEFAULT_SAMPLE_RATE = 1.0;

    /**
     * 默认直方图桶边界（毫秒）
     */
    public static final double[] DEFAULT_HISTOGRAM_BUCKETS = {
            1, 5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000
    };

    /**
     * 默认百分位数
     */
    public static final double[] DEFAULT_PERCENTILES = {0.5, 0.75, 0.9, 0.95, 0.99};

    /**
     * 指标过期时间
     */
    public static final Duration METRIC_EXPIRY = Duration.ofMinutes(5);

    // ===========================
    // 追踪配置
    // ===========================

    /**
     * 默认追踪采样率
     */
    public static final double DEFAULT_TRACE_SAMPLE_RATE = 0.1;

    /**
     * 追踪ID长度
     */
    public static final int TRACE_ID_LENGTH = 32;

    /**
     * Span ID长度
     */
    public static final int SPAN_ID_LENGTH = 16;

    // ===========================
    // MDC Key 定义
    // ===========================

    /**
     * MDC 上下文 Key 常量
     */
    public static final class MdcKey {
        private MdcKey() {}

        /**
         * 追踪ID
         */
        public static final String TRACE_ID = "traceId";

        /**
         * Span ID
         */
        public static final String SPAN_ID = "spanId";

        /**
         * 父Span ID
         */
        public static final String PARENT_SPAN_ID = "parentSpanId";

        /**
         * 用户ID
         */
        public static final String USER_ID = "userId";

        /**
         * 租户ID
         */
        public static final String TENANT_ID = "tenantId";

        /**
         * 请求ID
         */
        public static final String REQUEST_ID = "requestId";

        /**
         * 请求URI
         */
        public static final String REQUEST_URI = "requestUri";

        /**
         * 请求方法
         */
        public static final String REQUEST_METHOD = "requestMethod";

        /**
         * 客户端IP
         */
        public static final String CLIENT_IP = "clientIp";

        /**
         * 业务模块
         */
        public static final String MODULE = "module";

        /**
         * 业务操作
         */
        public static final String OPERATION = "operation";

        /**
         * 日志类别
         */
        public static final String LOG_CATEGORY = "logCategory";
    }

    // ===========================
    // 指标名称前缀
    // ===========================

    /**
     * 指标名称命名空间
     */
    public static final class MetricPrefix {
        private MetricPrefix() {}

        /**
         * 全局指标前缀
         */
        public static final String GLOBAL = "admin_";

        /**
         * HTTP指标前缀
         */
        public static final String HTTP = GLOBAL + "http_";

        /**
         * SQL指标前缀
         */
        public static final String SQL = GLOBAL + "sql_";

        /**
         * 业务指标前缀
         */
        public static final String BUSINESS = GLOBAL + "business_";

        /**
         * 缓存指标前缀
         */
        public static final String CACHE = GLOBAL + "cache_";

        /**
         * 消息指标前缀
         */
        public static final String MESSAGING = GLOBAL + "messaging_";

        /**
         * 安全指标前缀
         */
        public static final String SECURITY = GLOBAL + "security_";

        /**
         * 系统指标前缀
         */
        public static final String SYSTEM = GLOBAL + "system_";
    }

    // ===========================
    // 预定义指标名称
    // ===========================

    /**
     * HTTP 指标名称
     */
    public static final class HttpMetric {
        private HttpMetric() {}

        /**
         * HTTP请求总数
         */
        public static final String REQUESTS_TOTAL = MetricPrefix.HTTP + "requests_total";
        
        /**
         * HTTP请求总数（别名）
         */
        public static final String REQUEST_TOTAL = REQUESTS_TOTAL;

        /**
         * HTTP请求持续时间
         */
        public static final String REQUEST_DURATION = MetricPrefix.HTTP + "request_duration_seconds";

        /**
         * HTTP请求大小
         */
        public static final String REQUEST_SIZE = MetricPrefix.HTTP + "request_size_bytes";

        /**
         * HTTP响应大小
         */
        public static final String RESPONSE_SIZE = MetricPrefix.HTTP + "response_size_bytes";

        /**
         * HTTP错误总数
         */
        public static final String ERRORS_TOTAL = MetricPrefix.HTTP + "errors_total";

        /**
         * 当前活跃请求数
         */
        public static final String ACTIVE_REQUESTS = MetricPrefix.HTTP + "active_requests";
    }

    /**
     * SQL 指标名称
     */
    public static final class SqlMetric {
        private SqlMetric() {}

        /**
         * SQL查询总数
         */
        public static final String QUERIES_TOTAL = MetricPrefix.SQL + "queries_total";
        
        /**
         * SQL查询总数（别名）
         */
        public static final String QUERY_TOTAL = QUERIES_TOTAL;

        /**
         * SQL查询持续时间
         */
        public static final String QUERY_DURATION = MetricPrefix.SQL + "query_duration_seconds";

        /**
         * SQL错误总数
         */
        public static final String ERRORS_TOTAL = MetricPrefix.SQL + "errors_total";

        /**
         * 慢查询总数
         */
        public static final String SLOW_QUERIES_TOTAL = MetricPrefix.SQL + "slow_queries_total";
        
        /**
         * 慢查询总数（别名）
         */
        public static final String SLOW_QUERY_TOTAL = SLOW_QUERIES_TOTAL;

        /**
         * 受影响的行数
         */
        public static final String AFFECTED_ROWS = MetricPrefix.SQL + "affected_rows_total";
    }

    /**
     * 业务指标名称
     */
    public static final class BusinessMetric {
        private BusinessMetric() {}

        /**
         * 操作成功总数
         */
        public static final String OPERATIONS_SUCCESS = MetricPrefix.BUSINESS + "operations_success_total";

        /**
         * 操作失败总数
         */
        public static final String OPERATIONS_FAILURE = MetricPrefix.BUSINESS + "operations_failure_total";
        
        /**
         * 操作总数（成功+失败）
         */
        public static final String OPERATION_TOTAL = MetricPrefix.BUSINESS + "operation_total";
        
        /**
         * 操作失败总数（别名）
         */
        public static final String OPERATION_FAILURE = OPERATIONS_FAILURE;

        /**
         * 操作持续时间
         */
        public static final String OPERATION_DURATION = MetricPrefix.BUSINESS + "operation_duration_seconds";

        /**
         * 登录次数
         */
        public static final String LOGIN_TOTAL = MetricPrefix.BUSINESS + "login_total";

        /**
         * 注册次数
         */
        public static final String REGISTER_TOTAL = MetricPrefix.BUSINESS + "register_total";
    }

    /**
     * 安全指标名称
     */
    public static final class SecurityMetric {
        private SecurityMetric() {}

        /**
         * 认证成功总数
         */
        public static final String AUTH_SUCCESS_TOTAL = MetricPrefix.SECURITY + "auth_success_total";

        /**
         * 认证失败总数
         */
        public static final String AUTH_FAILURE_TOTAL = MetricPrefix.SECURITY + "auth_failure_total";

        /**
         * 授权拒绝总数
         */
        public static final String ACCESS_DENIED_TOTAL = MetricPrefix.SECURITY + "access_denied_total";

        /**
         * 可疑活动总数
         */
        public static final String SUSPICIOUS_ACTIVITY_TOTAL = MetricPrefix.SECURITY + "suspicious_activity_total";
    }

    // ===========================
    // 标签名称定义
    // ===========================

    /**
     * 通用标签名称
     */
    public static final class TagName {
        private TagName() {}

        /**
         * 方法
         */
        public static final String METHOD = "method";

        /**
         * URI/路径
         */
        public static final String URI = "uri";

        /**
         * 状态码
         */
        public static final String STATUS = "status";

        /**
         * 状态码类别（2xx, 4xx, 5xx）
         */
        public static final String STATUS_CLASS = "status_class";

        /**
         * 操作
         */
        public static final String OPERATION = "operation";

        /**
         * 模块
         */
        public static final String MODULE = "module";

        /**
         * 结果
         */
        public static final String RESULT = "result";

        /**
         * 异常类型
         */
        public static final String EXCEPTION = "exception";

        /**
         * 数据库操作类型
         */
        public static final String SQL_OPERATION = "sql_operation";

        /**
         * 表名
         */
        public static final String TABLE = "table";

        /**
         * 租户
         */
        public static final String TENANT = "tenant";

        /**
         * 用户
         */
        public static final String USER = "user";
    }

    // ===========================
    // 标签值定义
    // ===========================

    /**
     * 结果标签值
     */
    public static final class ResultValue {
        private ResultValue() {}

        public static final String SUCCESS = "success";
        public static final String FAILURE = "failure";
        public static final String ERROR = "error";
        public static final String TIMEOUT = "timeout";
        public static final String CANCEL = "cancel";
    }

    /**
     * SQL操作类型标签值
     */
    public static final class SqlOperationValue {
        private SqlOperationValue() {}

        public static final String SELECT = "SELECT";
        public static final String INSERT = "INSERT";
        public static final String UPDATE = "UPDATE";
        public static final String DELETE = "DELETE";
        public static final String DDL = "DDL";
        public static final String UNKNOWN = "UNKNOWN";
    }

    // ===========================
    // 日志格式化
    // ===========================

    /**
     * 日志格式化符号
     */
    public static final class LogFormat {
        private LogFormat() {}

        /**
         * 分隔线
         */
        public static final String SEPARATOR = "────────────────────────────────────────────────────────────────";

        /**
         * 短分隔线
         */
        public static final String SHORT_SEPARATOR = "────────────────────────────";

        /**
         * 箭头符号（请求方向）
         */
        public static final String ARROW_IN = "→";

        /**
         * 箭头符号（响应方向）
         */
        public static final String ARROW_OUT = "←";

        /**
         * 点符号
         */
        public static final String BULLET = "•";

        /**
         * 换行符
         */
        public static final String NEW_LINE = "\n";

        /**
         * 缩进（2空格）
         */
        public static final String INDENT = "  ";

        /**
         * 双缩进（4空格）
         */
        public static final String DOUBLE_INDENT = "    ";

        /**
         * 竖线（用于多行日志对齐）
         */
        public static final String PIPE = "│";
    }

    // ===========================
    // 慢查询阈值
    // ===========================

    /**
     * 慢查询阈值（毫秒）
     */
    public static final long SLOW_QUERY_THRESHOLD_MS = 1000L;

    /**
     * 慢请求阈值（毫秒）
     */
    public static final long SLOW_REQUEST_THRESHOLD_MS = 3000L;

    /**
     * 慢操作阈值（毫秒）
     */
    public static final long SLOW_OPERATION_THRESHOLD_MS = 5000L;

    // ===========================
    // 配置Key
    // ===========================

    /**
     * 配置Key定义
     */
    public static final class ConfigKey {
        private ConfigKey() {}

        public static final String PREFIX = "admin.observable";
        public static final String ENABLED = PREFIX + ".enabled";
        public static final String LOG_ENABLED = PREFIX + ".log.enabled";
        public static final String HTTP_LOG_ENABLED = PREFIX + ".log.http.enabled";
        public static final String SQL_LOG_ENABLED = PREFIX + ".log.sql.enabled";
        public static final String BUSINESS_LOG_ENABLED = PREFIX + ".log.business.enabled";
        public static final String METRICS_ENABLED = PREFIX + ".metrics.enabled";
        public static final String TRACE_ENABLED = PREFIX + ".trace.enabled";
        public static final String TRACE_SAMPLE_RATE = PREFIX + ".trace.sample-rate";
    }
}
