package io.github.faustofan.admin.shared.observable.logging;

import io.github.faustofan.admin.shared.observable.config.ObservableConfig;
import io.github.faustofan.admin.shared.observable.constants.LogCategory;
import io.github.faustofan.admin.shared.observable.constants.ObservableConstants;
import io.github.faustofan.admin.shared.observable.context.TraceContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * HTTP日志格式化器
 * <p>
 * 提供HTTP请求/响应的多行美化日志格式化能力
 *
 * <h3>输出示例：</h3>
 * <pre>
 * ┌────────────────────────────────────────────────────────────────
 * │ 🌐 HTTP Request
 * ├────────────────────────────────
 * │ → POST /api/v1/users/login
 * │ • TraceId: abc123def456
 * │ • Client IP: 192.168.1.100
 * ├────────────────────────────────
 * │ Headers:
 * │   Content-Type: application/json
 * │   User-Agent: Mozilla/5.0...
 * │   Authorization: [MASKED]
 * ├────────────────────────────────
 * │ Body:
 * │   {
 * │     "username": "admin",
 * │     "password": "[MASKED]"
 * │   }
 * └────────────────────────────────────────────────────────────────
 * </pre>
 */
@ApplicationScoped
public class HttpLogFormatter {

    private static final Logger LOG = Logger.getLogger(HttpLogFormatter.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    // 敏感字段模式（用于脱敏）
    private static final Pattern SENSITIVE_BODY_PATTERN = Pattern.compile(
            "(\"(?:password|secret|token|apiKey|api_key|accessToken|access_token|refreshToken|refresh_token|credential)\"\\s*:\\s*)\"[^\"]*\"",
            Pattern.CASE_INSENSITIVE
    );

    private final ObservableConfig config;
    private final Set<String> sensitiveHeaders;

    @Inject
    public HttpLogFormatter(ObservableConfig config) {
        this.config = config;
        this.sensitiveHeaders = parseSensitiveHeaders(config.log().http().sensitiveHeaders());
    }

    private Set<String> parseSensitiveHeaders(String headers) {
        if (headers == null || headers.isBlank()) {
            return Set.of("Authorization", "Cookie", "Set-Cookie");
        }
        return Arrays.stream(headers.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    // ===========================
    // 请求日志
    // ===========================

    /**
     * 格式化HTTP请求日志
     */
    public String formatRequest(HttpRequestInfo request) {
        if (!config.log().http().enabled()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        String separator = ObservableConstants.LogFormat.SEPARATOR;
        String shortSep = ObservableConstants.LogFormat.SHORT_SEPARATOR;
        String pipe = ObservableConstants.LogFormat.PIPE;
        String arrow = ObservableConstants.LogFormat.ARROW_IN;
        String bullet = ObservableConstants.LogFormat.BULLET;
        String indent = ObservableConstants.LogFormat.INDENT;

        // 顶部边框
        sb.append("\n┌").append(separator).append("\n");
        
        // 标题
        sb.append(pipe).append(" ").append(LogCategory.HTTP.getIcon())
                .append(" HTTP Request\n");
        
        // 分隔线
        sb.append("├").append(shortSep).append("\n");
        
        // 请求行
        sb.append(pipe).append(" ").append(arrow).append(" ")
                .append(request.method).append(" ").append(request.uri).append("\n");
        
        // Trace信息
        String traceId = TraceContext.currentTraceId();
        if (traceId != null) {
            sb.append(pipe).append(" ").append(bullet).append(" TraceId: ")
                    .append(traceId).append("\n");
        }
        
        // 客户端IP
        if (request.clientIp != null) {
            sb.append(pipe).append(" ").append(bullet).append(" Client IP: ")
                    .append(request.clientIp).append("\n");
        }
        
        // 时间戳
        sb.append(pipe).append(" ").append(bullet).append(" Time: ")
                .append(TIME_FORMATTER.format(Instant.now())).append("\n");

        // Headers
        if (config.log().http().logHeaders() && request.headers != null && !request.headers.isEmpty()) {
            sb.append("├").append(shortSep).append("\n");
            sb.append(pipe).append(" Headers:\n");
            request.headers.forEach((name, values) -> {
                String value = isSensitiveHeader(name) ? "[MASKED]" : String.join(", ", values);
                sb.append(pipe).append(indent).append(name).append(": ").append(value).append("\n");
            });
        }

        // Body
        if (config.log().http().logBody() && request.body != null && !request.body.isBlank()) {
            sb.append("├").append(shortSep).append("\n");
            sb.append(pipe).append(" Body:\n");
            String maskedBody = maskSensitiveData(request.body);
            String truncatedBody = truncateBody(maskedBody, config.log().http().maxBodyLength());
            formatMultilineBody(sb, truncatedBody, pipe, indent);
        }

        // 底部边框
        sb.append("└").append(separator);

        return sb.toString();
    }

    /**
     * 格式化HTTP响应日志
     */
    public String formatResponse(HttpResponseInfo response) {
        if (!config.log().http().enabled()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        String separator = ObservableConstants.LogFormat.SEPARATOR;
        String shortSep = ObservableConstants.LogFormat.SHORT_SEPARATOR;
        String pipe = ObservableConstants.LogFormat.PIPE;
        String arrow = ObservableConstants.LogFormat.ARROW_OUT;
        String bullet = ObservableConstants.LogFormat.BULLET;
        String indent = ObservableConstants.LogFormat.INDENT;

        // 顶部边框
        sb.append("\n┌").append(separator).append("\n");
        
        // 标题
        sb.append(pipe).append(" ").append(LogCategory.HTTP.getIcon())
                .append(" HTTP Response\n");
        
        // 分隔线
        sb.append("├").append(shortSep).append("\n");
        
        // 响应行
        sb.append(pipe).append(" ").append(arrow).append(" ")
                .append(response.status).append(" ")
                .append(getStatusText(response.status)).append("\n");
        
        // Trace信息
        String traceId = TraceContext.currentTraceId();
        if (traceId != null) {
            sb.append(pipe).append(" ").append(bullet).append(" TraceId: ")
                    .append(traceId).append("\n");
        }
        
        // 耗时
        sb.append(pipe).append(" ").append(bullet).append(" Duration: ")
                .append(response.durationMs).append("ms");
        if (response.durationMs > config.log().http().slowThresholdMs()) {
            sb.append(" ⚠️ SLOW");
        }
        sb.append("\n");

        // Headers
        if (config.log().http().logHeaders() && response.headers != null && !response.headers.isEmpty()) {
            sb.append("├").append(shortSep).append("\n");
            sb.append(pipe).append(" Headers:\n");
            response.headers.forEach((name, values) -> {
                String value = isSensitiveHeader(name) ? "[MASKED]" : String.join(", ", values);
                sb.append(pipe).append(indent).append(name).append(": ").append(value).append("\n");
            });
        }

        // Body
        if (config.log().http().logBody() && response.body != null && !response.body.isBlank()) {
            sb.append("├").append(shortSep).append("\n");
            sb.append(pipe).append(" Body:\n");
            String truncatedBody = truncateBody(response.body, config.log().http().maxBodyLength());
            formatMultilineBody(sb, truncatedBody, pipe, indent);
        }

        // 底部边框
        sb.append("└").append(separator);

        return sb.toString();
    }

    /**
     * 记录HTTP请求日志
     */
    public void logRequest(HttpRequestInfo request) {
        if (shouldSkipUri(request.uri)) {
            return;
        }
        String formatted = formatRequest(request);
        if (!formatted.isEmpty()) {
            LOG.info(formatted);
        }
    }

    /**
     * 记录HTTP响应日志
     */
    public void logResponse(HttpResponseInfo response) {
        if (shouldSkipUri(response.uri)) {
            return;
        }
        String formatted = formatResponse(response);
        if (!formatted.isEmpty()) {
            if (response.status >= 400 || response.durationMs > config.log().http().slowThresholdMs()) {
                LOG.warn(formatted);
            } else {
                LOG.info(formatted);
            }
        }
    }

    // ===========================
    // 辅助方法
    // ===========================

    private boolean isSensitiveHeader(String headerName) {
        return sensitiveHeaders.contains(headerName.toLowerCase());
    }

    private String maskSensitiveData(String body) {
        if (body == null) return null;
        return SENSITIVE_BODY_PATTERN.matcher(body).replaceAll("$1\"[MASKED]\"");
    }

    private String truncateBody(String body, int maxLength) {
        if (body == null || body.length() <= maxLength) {
            return body;
        }
        return body.substring(0, maxLength) + "... [truncated]";
    }

    private void formatMultilineBody(StringBuilder sb, String body, String pipe, String indent) {
        if (body == null) return;
        String[] lines = body.split("\n");
        for (String line : lines) {
            sb.append(pipe).append(indent).append(line).append("\n");
        }
    }

    private boolean shouldSkipUri(String uri) {
        if (uri == null) return true;
        
        Optional<String> patterns = config.log().http().excludePatterns();
        if (patterns.isEmpty()) {
            // 默认排除健康检查和metrics端点
            return uri.contains("/q/health") || uri.contains("/q/metrics") || uri.contains("/q/dev");
        }
        
        return Pattern.compile(patterns.get()).matcher(uri).matches();
    }

    private String getStatusText(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 204 -> "No Content";
            case 301 -> "Moved Permanently";
            case 302 -> "Found";
            case 304 -> "Not Modified";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 409 -> "Conflict";
            case 422 -> "Unprocessable Entity";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            case 504 -> "Gateway Timeout";
            default -> "";
        };
    }

    // ===========================
    // 数据类
    // ===========================

    /**
     * HTTP请求信息
     */
    public static class HttpRequestInfo {
        private String method;
        private String uri;
        private String clientIp;
        private Map<String, List<String>> headers;
        private String body;
        private long startTime;

        private HttpRequestInfo() {}

        public static Builder builder() {
            return new Builder();
        }

        public String getMethod() { return method; }
        public String getUri() { return uri; }
        public String getClientIp() { return clientIp; }
        public Map<String, List<String>> getHeaders() { return headers; }
        public String getBody() { return body; }
        public long getStartTime() { return startTime; }

        public static class Builder {
            private final HttpRequestInfo info = new HttpRequestInfo();

            public Builder method(String method) { info.method = method; return this; }
            public Builder uri(String uri) { info.uri = uri; return this; }
            public Builder clientIp(String clientIp) { info.clientIp = clientIp; return this; }
            public Builder headers(Map<String, List<String>> headers) { info.headers = headers; return this; }
            public Builder body(String body) { info.body = body; return this; }
            public Builder startTime(long startTime) { info.startTime = startTime; return this; }

            public HttpRequestInfo build() {
                if (info.startTime == 0) {
                    info.startTime = System.currentTimeMillis();
                }
                return info;
            }
        }
    }

    /**
     * HTTP响应信息
     */
    public static class HttpResponseInfo {
        private String uri;
        private int status;
        private Map<String, List<String>> headers;
        private String body;
        private long durationMs;

        private HttpResponseInfo() {}

        public static Builder builder() {
            return new Builder();
        }

        public String getUri() { return uri; }
        public int getStatus() { return status; }
        public Map<String, List<String>> getHeaders() { return headers; }
        public String getBody() { return body; }
        public long getDurationMs() { return durationMs; }

        public static class Builder {
            private final HttpResponseInfo info = new HttpResponseInfo();

            public Builder uri(String uri) { info.uri = uri; return this; }
            public Builder status(int status) { info.status = status; return this; }
            public Builder headers(Map<String, List<String>> headers) { info.headers = headers; return this; }
            public Builder body(String body) { info.body = body; return this; }
            public Builder durationMs(long durationMs) { info.durationMs = durationMs; return this; }

            public HttpResponseInfo build() {
                return info;
            }
        }
    }
}
