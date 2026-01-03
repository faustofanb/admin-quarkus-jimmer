package io.github.faustofan.admin.shared.observable;

import io.github.faustofan.admin.shared.observable.config.ObservableConfig;
import io.github.faustofan.admin.shared.observable.constants.ObservableConstants;
import io.github.faustofan.admin.shared.observable.logging.BusinessLogger;
import io.github.faustofan.admin.shared.observable.logging.HttpLogFormatter;
import io.github.faustofan.admin.shared.observable.logging.HttpLogFormatter.HttpRequestInfo;
import io.github.faustofan.admin.shared.observable.logging.HttpLogFormatter.HttpResponseInfo;
import io.github.faustofan.admin.shared.observable.logging.SqlLogFormatter;
import io.github.faustofan.admin.shared.observable.logging.SqlLogFormatter.SqlInfo;
import io.github.faustofan.admin.shared.observable.metrics.MetricsRecorder;
import io.github.faustofan.admin.shared.observable.context.TraceContext;
import io.github.faustofan.admin.shared.observable.constants.LogLevel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;

/**
 * ObservableFacade 提供统一的可观测性入口，封装业务日志、HTTP/SQL 日志、指标记录以及链路追踪。
 * <p>
 * 通过 Facade，业务代码只需要依赖此类即可完成所有可观测性相关的操作，避免在业务层散布各类实现细节。
 */
@ApplicationScoped
public class ObservableFacade {

    private final BusinessLogger businessLogger;
    private final HttpLogFormatter httpLogFormatter;
    private final SqlLogFormatter sqlLogFormatter;
    private final MetricsRecorder metricsRecorder;
    private final ObservableConfig config;

    @Inject
    public ObservableFacade(
            BusinessLogger businessLogger,
            HttpLogFormatter httpLogFormatter,
            SqlLogFormatter sqlLogFormatter,
            MetricsRecorder metricsRecorder,
            ObservableConfig config
    ) {
        this.businessLogger = businessLogger;
        this.httpLogFormatter = httpLogFormatter;
        this.sqlLogFormatter = sqlLogFormatter;
        this.metricsRecorder = metricsRecorder;
        this.config = config;
    }
    
    public ObservableConfig getConfig() {
        return config;
    }

    // ===========================
    // Business Logging
    // ===========================

    /**
     * 记录业务日志。
     *
     * @param module   业务模块名称
     * @param operation 业务操作名称
     * @param level    日志级别
     * @param message  日志内容
     */
    public void logBusiness(String module, String operation, LogLevel level, String message) {
        businessLogger.log(level, io.github.faustofan.admin.shared.observable.constants.LogCategory.BUSINESS, module, operation, message, null, null);
    }

    // ===========================
    // HTTP Logging
    // ===========================

    /**
     * 记录 HTTP 请求日志。
     */
    public void logHttpRequest(HttpRequestInfo requestInfo) {
        httpLogFormatter.logRequest(requestInfo);
    }

    /**
     * 记录 HTTP 响应日志。
     */
    public void logHttpResponse(HttpResponseInfo responseInfo) {
        httpLogFormatter.logResponse(responseInfo);
    }

    // ===========================
    // SQL Logging
    // ===========================

    /**
     * 记录 SQL 日志。
     */
    public void logSql(SqlInfo sqlInfo) {
        // 记录格式化日志
        sqlLogFormatter.log(sqlInfo);
        
        // 自动收集 SQL 指标
        String sqlType = detectSqlType(sqlInfo.getSql());
        String tableName = extractTableName(sqlInfo.getSql());
        
        Map<String, String> tags = new java.util.HashMap<>();
        tags.put("type", sqlType);
        if (tableName != null) {
            tags.put("table", tableName);
        }
        
        // 记录 SQL 查询计数
        metricsRecorder.incrementCounter(
            ObservableConstants.SqlMetric.QUERY_TOTAL,
            tags
        );
        
        // 记录 SQL 查询耗时
        metricsRecorder.recordTimer(
            ObservableConstants.SqlMetric.QUERY_DURATION,
            sqlInfo.getDurationMs(),
            tags
        );
        
        // 如果是慢查询，额外记录
        if (sqlInfo.getDurationMs() > config.log().sql().slowThresholdMs()) {
            Map<String, String> slowTags = new java.util.HashMap<>(tags);
            slowTags.put("slow", "true");
            metricsRecorder.incrementCounter(
                ObservableConstants.SqlMetric.SLOW_QUERY_TOTAL,
                slowTags
            );
        }
    }
    
    // Helper methods for SQL metrics
    private String detectSqlType(String sql) {
        if (sql == null) return "UNKNOWN";
        String upper = sql.trim().toUpperCase();
        if (upper.startsWith("SELECT")) return "SELECT";
        if (upper.startsWith("INSERT")) return "INSERT";
        if (upper.startsWith("UPDATE")) return "UPDATE";
        if (upper.startsWith("DELETE")) return "DELETE";
        if (upper.startsWith("CREATE") || upper.startsWith("ALTER") || upper.startsWith("DROP")) return "DDL";
        return "OTHER";
    }
    
    private String extractTableName(String sql) {
        if (sql == null) return null;
        // Simple extraction - can be improved
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "\\b(?:FROM|INTO|UPDATE|TABLE)\\s+([\\w.]+)",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher matcher = pattern.matcher(sql);
        return matcher.find() ? matcher.group(1) : null;
    }

    // ===========================
    // Metrics
    // ===========================

    /**
     * 增加计数器指标。
     */
    public void incrementCounter(String metricName, Map<String, String> tags) {
        metricsRecorder.incrementCounter(metricName, tags);
    }

    /**
     * 记录计时器指标（毫秒）。
     */
    public void recordTimer(String metricName, long durationMs, Map<String, String> tags) {
        metricsRecorder.recordTimer(metricName, durationMs, tags);
    }

    /**
     * 记录直方图或分布摘要指标。
     */
    public void recordHistogram(String metricName, double value, Map<String, String> tags) {
        metricsRecorder.recordHistogram(metricName, value, tags);
    }

    // ===========================
    // Tracing
    // ===========================

    /**
     * 开始一个新的 Trace 并返回 TraceId。可在业务代码中自行保存返回值以便后续使用。
     */
    public String startTrace() {
        TraceContext ctx = TraceContext.create();
        ctx.pushToMdc();
        return ctx.getTraceId();
    }

    /**
     * 结束当前 Trace。
     */
    public void endTrace() {
        TraceContext.clearMdc();
    }

    /**
     * 在当前 Trace 中创建子 Span。
     */
    public String startSpan(String spanName) {
        return TraceContext.current()
                .map(ctx -> {
                    TraceContext child = ctx.createChild();
                    child.pushToMdc();
                    return child.getSpanId();
                })
                .orElseGet(() -> {
                     // 如果没有当前 Trace，则开启一个新的
                     return startTrace();
                });
    }

    /**
     * 结束当前 Span。
     */
    public void endSpan() {
         // 注意：基于MDC的简单实现难以精确恢复父Span，
         // 这里仅简单移除当前 TraceContext 信息。
         // 如需严谨的 Span 栈管理，建议使用真正的 Tracing 框架（如 OTel SDK）
         // 或者在 TraceContext 中维护 Stack。
         TraceContext.current().ifPresent(TraceContext::removeFromMdc);
    }
}
