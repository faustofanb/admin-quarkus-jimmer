package io.github.faustofan.admin.shared.observable.logging;

import io.github.faustofan.admin.shared.observable.config.ObservableConfig;
import io.github.faustofan.admin.shared.observable.constants.LogCategory;
import io.github.faustofan.admin.shared.observable.constants.ObservableConstants;
import io.github.faustofan.admin.shared.observable.context.TraceContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL日志格式化器
 * <p>
 * 提供SQL语句的多行美化日志格式化能力
 *
 * <h3>输出示例：</h3>
 * <pre>
 * ┌────────────────────────────────────────────────────────────────
 * │ 🗄️ SQL Query
 * ├────────────────────────────────
 * │ • Type: SELECT
 * │ • Table: system_user
 * │ • TraceId: abc123def456
 * │ • Duration: 15ms
 * ├────────────────────────────────
 * │ SQL:
 * │   SELECT
 * │       u.id,
 * │       u.username,
 * │       u.email
 * │   FROM
 * │       system_user u
 * │   WHERE
 * │       u.tenant_id = ?
 * │       AND u.deleted = ?
 * │   ORDER BY
 * │       u.create_time DESC
 * │   LIMIT ?
 * ├────────────────────────────────
 * │ Parameters: [1, 0, 10]
 * │ Rows affected: 5
 * └────────────────────────────────────────────────────────────────
 * </pre>
 */
@ApplicationScoped
public class SqlLogFormatter {

    private static final Logger LOG = Logger.getLogger(SqlLogFormatter.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    // SQL关键字（用于格式化）
    private static final Set<String> SQL_KEYWORDS = Set.of(
            "SELECT", "FROM", "WHERE", "AND", "OR", "ORDER BY", "GROUP BY",
            "HAVING", "LIMIT", "OFFSET", "LEFT JOIN", "RIGHT JOIN", "INNER JOIN",
            "OUTER JOIN", "JOIN", "ON", "INSERT INTO", "VALUES", "UPDATE", "SET",
            "DELETE FROM", "CREATE TABLE", "ALTER TABLE", "DROP TABLE",
            "CREATE INDEX", "DROP INDEX", "UNION", "UNION ALL", "DISTINCT",
            "AS", "IN", "NOT IN", "BETWEEN", "LIKE", "IS NULL", "IS NOT NULL",
            "EXISTS", "NOT EXISTS", "CASE", "WHEN", "THEN", "ELSE", "END"
    );

    // SQL类型识别模式
    private static final Pattern SELECT_PATTERN = Pattern.compile("^\\s*SELECT\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern INSERT_PATTERN = Pattern.compile("^\\s*INSERT\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern UPDATE_PATTERN = Pattern.compile("^\\s*UPDATE\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DELETE_PATTERN = Pattern.compile("^\\s*DELETE\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DDL_PATTERN = Pattern.compile("^\\s*(CREATE|ALTER|DROP|TRUNCATE)\\b", Pattern.CASE_INSENSITIVE);

    // 表名提取模式
    private static final Pattern FROM_TABLE_PATTERN = Pattern.compile("\\bFROM\\s+([\\w.]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern INSERT_TABLE_PATTERN = Pattern.compile("\\bINSERT\\s+INTO\\s+([\\w.]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern UPDATE_TABLE_PATTERN = Pattern.compile("\\bUPDATE\\s+([\\w.]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DELETE_TABLE_PATTERN = Pattern.compile("\\bDELETE\\s+FROM\\s+([\\w.]+)", Pattern.CASE_INSENSITIVE);

    private final ObservableConfig config;

    @Inject
    public SqlLogFormatter(ObservableConfig config) {
        this.config = config;
    }

    // ===========================
    // 日志格式化
    // ===========================

    /**
     * 格式化SQL日志
     */
    public String format(SqlInfo sqlInfo) {
        if (!config.log().sql().enabled()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        String separator = ObservableConstants.LogFormat.SEPARATOR;
        String shortSep = ObservableConstants.LogFormat.SHORT_SEPARATOR;
        String pipe = ObservableConstants.LogFormat.PIPE;
        String bullet = ObservableConstants.LogFormat.BULLET;
        String indent = ObservableConstants.LogFormat.INDENT;

        // 顶部边框
        sb.append("\n┌").append(separator).append("\n");
        
        // 标题
        sb.append(pipe).append(" ").append(LogCategory.SQL.getIcon())
                .append(" SQL Query\n");
        
        // 分隔线
        sb.append("├").append(shortSep).append("\n");
        
        // SQL类型
        String sqlType = detectSqlType(sqlInfo.sql);
        sb.append(pipe).append(" ").append(bullet).append(" Type: ")
                .append(sqlType).append("\n");
        
        // 表名
        String tableName = extractTableName(sqlInfo.sql);
        if (tableName != null) {
            sb.append(pipe).append(" ").append(bullet).append(" Table: ")
                    .append(tableName).append("\n");
        }
        
        // 连接信息（数据源识别）
        if (sqlInfo.connectionUrl != null) {
            String datasourceName = extractDatasourceName(sqlInfo.connectionUrl);
            sb.append(pipe).append(" ").append(bullet).append(" Datasource: ")
                    .append(datasourceName)
                    .append(" (conn#").append(sqlInfo.connectionId).append(")\n");
        }
        
        // Trace信息
        String traceId = TraceContext.currentTraceId();
        if (traceId != null) {
            sb.append(pipe).append(" ").append(bullet).append(" TraceId: ")
                    .append(traceId).append("\n");
        }
        
        // 耗时
        sb.append(pipe).append(" ").append(bullet).append(" Duration: ")
                .append(sqlInfo.durationMs).append("ms");
        if (sqlInfo.durationMs > config.log().sql().slowThresholdMs()) {
            sb.append(" ⚠️ SLOW QUERY");
        }
        sb.append("\n");

        // SQL语句（使用 sql-formatter）
        sb.append("├").append(shortSep).append("\n");
        sb.append(pipe).append(" SQL:\n");
        String formattedSql = config.log().sql().prettyPrint() 
                ? prettifySqlWithLibrary(sqlInfo.sql) 
                : sqlInfo.sql;
        String truncatedSql = truncateSql(formattedSql, config.log().sql().maxLength());
        formatMultilineSql(sb, truncatedSql, pipe, indent);

        // 影响行数
        if (config.log().sql().logRowCount() && sqlInfo.rowCount >= 0) {
            sb.append("├").append(shortSep).append("\n");
            sb.append(pipe).append(" Rows affected: ").append(sqlInfo.rowCount).append("\n");
        }
        
        // 调用栈（前 4 层）
        if (sqlInfo.stackTrace != null && !sqlInfo.stackTrace.isEmpty()) {
            sb.append("├").append(shortSep).append("\n");
            sb.append(pipe).append(" Call Stack (top 4):\n");
            for (String stackLine : sqlInfo.stackTrace) {
                sb.append(pipe).append(indent).append("↳ ").append(stackLine).append("\n");
            }
        }

        // 底部边框
        sb.append("└").append(separator);

        return sb.toString();
    }
    
    /**
     * 使用 sql-formatter 库进行专业的 SQL 格式化
     */
    private String prettifySqlWithLibrary(String sql) {
        if (sql == null || sql.isBlank()) {
            return sql;
        }
        
        try {
            // 使用 sql-formatter 库（正确的 API）
            // Dialect 已经移除，直接使用 format(String) 或 format(String, String)
            return com.github.vertical_blank.sqlformatter.SqlFormatter.format(sql);
        } catch (Exception e) {
            // 如果格式化失败，返回原始 SQL
            LOG.warn("Failed to format SQL with sql-formatter, using original", e);
            return sql;
        }
    }
    
    /**
     * 从 JDBC URL 中提取数据源名称
     */
    private String extractDatasourceName(String url) {
        if (url == null) return "unknown";
        
        try {
            // 示例 URL: jdbc:p6spy:postgresql://localhost:5432/admin
            // 提取 database 名称
            if (url.contains("p6spy:")) {
                url = url.substring(url.indexOf("p6spy:") + 6);
            }
            
            // 提取数据库类型和名称
            if (url.startsWith("jdbc:")) {
                String[] parts = url.split("[:/]");
                String dbType = parts.length > 1 ? parts[1] : "unknown";
                String dbName = parts.length > 0 ? parts[parts.length - 1] : "unknown";
                
                // 去除查询参数
                if (dbName.contains("?")) {
                    dbName = dbName.substring(0, dbName.indexOf("?"));
                }
                
                return dbType + ":" + dbName;
            }
            
            return url;
        } catch (Exception e) {
            return url;
        }
    }

    /**
     * 记录SQL日志
     */
    public void log(SqlInfo sqlInfo) {
        String formatted = format(sqlInfo);
        if (formatted.isEmpty()) {
            return;
        }
        
        if (sqlInfo.durationMs > config.log().sql().slowThresholdMs()) {
            LOG.warn(formatted);
        } else if (sqlInfo.error != null) {
            LOG.error(formatted, sqlInfo.error);
        } else {
            LOG.debug(formatted);
        }
    }

    /**
     * 快速记录SQL日志
     */
    public void log(String sql, List<Object> parameters, long durationMs) {
        log(SqlInfo.builder()
                .sql(sql)
                .parameters(parameters)
                .durationMs(durationMs)
                .build());
    }

    /**
     * 快速记录SQL日志（带行数）
     */
    public void log(String sql, List<Object> parameters, long durationMs, int rowCount) {
        log(SqlInfo.builder()
                .sql(sql)
                .parameters(parameters)
                .durationMs(durationMs)
                .rowCount(rowCount)
                .build());
    }

    // ===========================
    // SQL美化
    // ===========================

    /**
     * 美化SQL语句
     */
    public String prettifySql(String sql) {
        if (sql == null || sql.isBlank()) {
            return sql;
        }

        // 移除多余空白
        String normalized = sql.replaceAll("\\s+", " ").trim();
        
        StringBuilder result = new StringBuilder();
        int indentLevel = 0;
        String currentIndent = "";
        
        // 按空格分割处理
        String[] tokens = normalized.split(" ");
        StringBuilder currentLine = new StringBuilder();
        
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            String upperToken = token.toUpperCase();
            
            // 检查是否是需要换行的关键字
            if (shouldBreakBefore(upperToken)) {
                if (currentLine.length() > 0) {
                    result.append(currentIndent).append(currentLine.toString().trim()).append("\n");
                    currentLine = new StringBuilder();
                }
                
                // 调整缩进
                if (upperToken.equals("(")) {
                    indentLevel++;
                } else if (upperToken.equals(")")) {
                    indentLevel = Math.max(0, indentLevel - 1);
                }
                
                currentIndent = ObservableConstants.LogFormat.DOUBLE_INDENT.repeat(indentLevel + 1);
            }
            
            currentLine.append(token).append(" ");
        }
        
        // 添加最后一行
        if (currentLine.length() > 0) {
            result.append(currentIndent).append(currentLine.toString().trim());
        }
        
        return result.toString();
    }

    private boolean shouldBreakBefore(String token) {
        return switch (token) {
            case "SELECT", "FROM", "WHERE", "AND", "OR", "ORDER", "GROUP", 
                 "HAVING", "LIMIT", "OFFSET", "LEFT", "RIGHT", "INNER", 
                 "OUTER", "JOIN", "SET", "VALUES", "UNION" -> true;
            default -> false;
        };
    }

    // ===========================
    // 辅助方法
    // ===========================

    private String detectSqlType(String sql) {
        if (sql == null) return ObservableConstants.SqlOperationValue.UNKNOWN;
        
        if (SELECT_PATTERN.matcher(sql).find()) return ObservableConstants.SqlOperationValue.SELECT;
        if (INSERT_PATTERN.matcher(sql).find()) return ObservableConstants.SqlOperationValue.INSERT;
        if (UPDATE_PATTERN.matcher(sql).find()) return ObservableConstants.SqlOperationValue.UPDATE;
        if (DELETE_PATTERN.matcher(sql).find()) return ObservableConstants.SqlOperationValue.DELETE;
        if (DDL_PATTERN.matcher(sql).find()) return ObservableConstants.SqlOperationValue.DDL;
        
        return ObservableConstants.SqlOperationValue.UNKNOWN;
    }

    private String extractTableName(String sql) {
        if (sql == null) return null;
        
        Matcher matcher;
        
        matcher = FROM_TABLE_PATTERN.matcher(sql);
        if (matcher.find()) return matcher.group(1);
        
        matcher = INSERT_TABLE_PATTERN.matcher(sql);
        if (matcher.find()) return matcher.group(1);
        
        matcher = UPDATE_TABLE_PATTERN.matcher(sql);
        if (matcher.find()) return matcher.group(1);
        
        matcher = DELETE_TABLE_PATTERN.matcher(sql);
        if (matcher.find()) return matcher.group(1);
        
        return null;
    }

    private String truncateSql(String sql, int maxLength) {
        if (sql == null || sql.length() <= maxLength) {
            return sql;
        }
        return sql.substring(0, maxLength) + "... [truncated]";
    }

    private void formatMultilineSql(StringBuilder sb, String sql, String pipe, String indent) {
        if (sql == null) return;
        String[] lines = sql.split("\n");
        for (String line : lines) {
            sb.append(pipe).append(indent).append(line).append("\n");
        }
    }

    private String formatParameters(List<Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "[]";
        }
        
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) sb.append(", ");
            Object param = parameters.get(i);
            if (param == null) {
                sb.append("null");
            } else if (param instanceof String) {
                String str = (String) param;
                if (str.length() > 100) {
                    sb.append("'").append(str.substring(0, 100)).append("...'");
                } else {
                    sb.append("'").append(str).append("'");
                }
            } else {
                sb.append(param);
            }
        }
        sb.append("]");
        return sb.toString();
    }

    // ===========================
    // 数据类
    // ===========================

    /**
     * SQL信息
     */
    public static class SqlInfo {
        private String sql;
        private List<Object> parameters;
        private long durationMs;
        private int rowCount = -1;
        private Throwable error;
        private String connectionUrl;      // 数据源连接 URL
        private int connectionId;          // 连接 ID
        private List<String> stackTrace;   // 调用栈（前 N 层）

        private SqlInfo() {}

        public static Builder builder() {
            return new Builder();
        }

        public String getSql() { return sql; }
        public List<Object> getParameters() { return parameters; }
        public long getDurationMs() { return durationMs; }
        public int getRowCount() { return rowCount; }
        public Throwable getError() { return error; }
        public String getConnectionUrl() { return connectionUrl; }
        public int getConnectionId() { return connectionId; }
        public List<String> getStackTrace() { return stackTrace; }

        public static class Builder {
            private final SqlInfo info = new SqlInfo();

            public Builder sql(String sql) { info.sql = sql; return this; }
            public Builder parameters(List<Object> parameters) { info.parameters = parameters; return this; }
            public Builder durationMs(long durationMs) { info.durationMs = durationMs; return this; }
            public Builder rowCount(int rowCount) { info.rowCount = rowCount; return this; }
            public Builder error(Throwable error) { info.error = error; return this; }
            public Builder connectionUrl(String connectionUrl) { info.connectionUrl = connectionUrl; return this; }
            public Builder connectionId(int connectionId) { info.connectionId = connectionId; return this; }
            public Builder stackTrace(List<String> stackTrace) { info.stackTrace = stackTrace; return this; }

            public SqlInfo build() {
                return info;
            }
        }
    }
}
