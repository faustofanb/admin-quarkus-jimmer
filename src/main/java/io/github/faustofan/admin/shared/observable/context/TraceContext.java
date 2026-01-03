package io.github.faustofan.admin.shared.observable.context;

import io.github.faustofan.admin.shared.observable.constants.ObservableConstants;
import org.jboss.logging.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 追踪上下文持有者
 * <p>
 * 管理追踪相关的上下文信息，与MDC集成
 */
public final class TraceContext {

    private final String traceId;
    private final String spanId;
    private final String parentSpanId;
    private final long startTime;
    private final Map<String, String> baggage;

    private TraceContext(Builder builder) {
        this.traceId = builder.traceId;
        this.spanId = builder.spanId;
        this.parentSpanId = builder.parentSpanId;
        this.startTime = builder.startTime;
        this.baggage = new HashMap<>(builder.baggage);
    }

    // ===========================
    // 静态工厂方法
    // ===========================

    /**
     * 创建新的追踪上下文
     */
    public static TraceContext create() {
        return builder()
                .traceId(generateTraceId())
                .spanId(generateSpanId())
                .build();
    }

    /**
     * 创建新的追踪上下文（指定traceId）
     */
    public static TraceContext create(String traceId) {
        return builder()
                .traceId(traceId != null ? traceId : generateTraceId())
                .spanId(generateSpanId())
                .build();
    }

    /**
     * 从当前MDC恢复上下文
     */
    public static Optional<TraceContext> current() {
        String traceId = (String) MDC.get(ObservableConstants.MdcKey.TRACE_ID);
        if (traceId == null) {
            return Optional.empty();
        }
        return Optional.of(builder()
                .traceId(traceId)
                .spanId((String) MDC.get(ObservableConstants.MdcKey.SPAN_ID))
                .parentSpanId((String) MDC.get(ObservableConstants.MdcKey.PARENT_SPAN_ID))
                .build());
    }

    /**
     * 获取当前traceId，如果不存在则生成新的
     */
    public static String currentTraceId() {
        String traceId = (String) MDC.get(ObservableConstants.MdcKey.TRACE_ID);
        return traceId != null ? traceId : generateTraceId();
    }

    /**
     * 获取当前spanId
     */
    public static String currentSpanId() {
        return (String) MDC.get(ObservableConstants.MdcKey.SPAN_ID);
    }

    // ===========================
    // ID生成
    // ===========================

    /**
     * 生成追踪ID（32位十六进制）
     */
    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成Span ID（16位十六进制）
     */
    public static String generateSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, ObservableConstants.SPAN_ID_LENGTH);
    }

    // ===========================
    // MDC操作
    // ===========================

    /**
     * 将上下文推入MDC
     */
    public void pushToMdc() {
        MDC.put(ObservableConstants.MdcKey.TRACE_ID, traceId);
        if (spanId != null) {
            MDC.put(ObservableConstants.MdcKey.SPAN_ID, spanId);
        }
        if (parentSpanId != null) {
            MDC.put(ObservableConstants.MdcKey.PARENT_SPAN_ID, parentSpanId);
        }
        baggage.forEach(MDC::put);
    }

    /**
     * 从MDC移除上下文
     */
    public void removeFromMdc() {
        MDC.remove(ObservableConstants.MdcKey.TRACE_ID);
        MDC.remove(ObservableConstants.MdcKey.SPAN_ID);
        MDC.remove(ObservableConstants.MdcKey.PARENT_SPAN_ID);
        baggage.keySet().forEach(MDC::remove);
    }

    /**
     * 设置MDC的用户信息
     */
    public static void setUserContext(String userId, String tenantId) {
        if (userId != null) {
            MDC.put(ObservableConstants.MdcKey.USER_ID, userId);
        }
        if (tenantId != null) {
            MDC.put(ObservableConstants.MdcKey.TENANT_ID, tenantId);
        }
    }

    /**
     * 设置MDC的请求信息
     */
    public static void setRequestContext(String requestId, String uri, String method, String clientIp) {
        if (requestId != null) {
            MDC.put(ObservableConstants.MdcKey.REQUEST_ID, requestId);
        }
        if (uri != null) {
            MDC.put(ObservableConstants.MdcKey.REQUEST_URI, uri);
        }
        if (method != null) {
            MDC.put(ObservableConstants.MdcKey.REQUEST_METHOD, method);
        }
        if (clientIp != null) {
            MDC.put(ObservableConstants.MdcKey.CLIENT_IP, clientIp);
        }
    }

    /**
     * 设置业务上下文
     */
    public static void setBusinessContext(String module, String operation) {
        if (module != null) {
            MDC.put(ObservableConstants.MdcKey.MODULE, module);
        }
        if (operation != null) {
            MDC.put(ObservableConstants.MdcKey.OPERATION, operation);
        }
    }

    /**
     * 清除所有MDC上下文
     */
    public static void clearMdc() {
        MDC.clear();
    }

    /**
     * 获取当前MDC快照
     */
    public static Map<String, String> getMdcSnapshot() {
        Map<String, String> snapshot = new HashMap<>();
        Object traceId = MDC.get(ObservableConstants.MdcKey.TRACE_ID);
        if (traceId != null) snapshot.put(ObservableConstants.MdcKey.TRACE_ID, traceId.toString());
        Object spanId = MDC.get(ObservableConstants.MdcKey.SPAN_ID);
        if (spanId != null) snapshot.put(ObservableConstants.MdcKey.SPAN_ID, spanId.toString());
        Object parentSpanId = MDC.get(ObservableConstants.MdcKey.PARENT_SPAN_ID);
        if (parentSpanId != null) snapshot.put(ObservableConstants.MdcKey.PARENT_SPAN_ID, parentSpanId.toString());
        Object userId = MDC.get(ObservableConstants.MdcKey.USER_ID);
        if (userId != null) snapshot.put(ObservableConstants.MdcKey.USER_ID, userId.toString());
        Object tenantId = MDC.get(ObservableConstants.MdcKey.TENANT_ID);
        if (tenantId != null) snapshot.put(ObservableConstants.MdcKey.TENANT_ID, tenantId.toString());
        Object requestUri = MDC.get(ObservableConstants.MdcKey.REQUEST_URI);
        if (requestUri != null) snapshot.put(ObservableConstants.MdcKey.REQUEST_URI, requestUri.toString());
        Object requestMethod = MDC.get(ObservableConstants.MdcKey.REQUEST_METHOD);
        if (requestMethod != null) snapshot.put(ObservableConstants.MdcKey.REQUEST_METHOD, requestMethod.toString());
        Object clientIp = MDC.get(ObservableConstants.MdcKey.CLIENT_IP);
        if (clientIp != null) snapshot.put(ObservableConstants.MdcKey.CLIENT_IP, clientIp.toString());
        return snapshot;
    }

    /**
     * 从快照恢复MDC
     */
    public static void restoreMdcFromSnapshot(Map<String, String> snapshot) {
        if (snapshot != null) {
            snapshot.forEach(MDC::put);
        }
    }

    // ===========================
    // 创建子Span
    // ===========================

    /**
     * 创建子Span上下文
     */
    public TraceContext createChild() {
        return builder()
                .traceId(this.traceId)
                .parentSpanId(this.spanId)
                .spanId(generateSpanId())
                .baggage(this.baggage)
                .build();
    }

    /**
     * Start a new child span and push its context to MDC.
     * Returns the new spanId.
     */
    public String startSpan(String spanName) {
        // spanName can be used for future extensions; currently ignored.
        TraceContext child = this.createChild();
        child.pushToMdc();
        return child.getSpanId();
    }

    /**
     * End the current span by removing span-related entries from MDC.
     */
    public void endSpan() {
        // Remove only span identifiers; other MDC entries (traceId, etc.) remain.
        MDC.remove(ObservableConstants.MdcKey.SPAN_ID);
        MDC.remove(ObservableConstants.MdcKey.PARENT_SPAN_ID);
    }

    // ===========================
    // Getters
    // ===========================

    public String getTraceId() {
        return traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    public String getParentSpanId() {
        return parentSpanId;
    }

    public long getStartTime() {
        return startTime;
    }

    public Map<String, String> getBaggage() {
        return new HashMap<>(baggage);
    }

    public Optional<String> getBaggageItem(String key) {
        return Optional.ofNullable(baggage.get(key));
    }

    /**
     * 计算经过的时间（毫秒）
     */
    public long getElapsedMs() {
        return System.currentTimeMillis() - startTime;
    }

    // ===========================
    // Builder
    // ===========================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String traceId;
        private String spanId;
        private String parentSpanId;
        private long startTime = System.currentTimeMillis();
        private Map<String, String> baggage = new HashMap<>();

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder spanId(String spanId) {
            this.spanId = spanId;
            return this;
        }

        public Builder parentSpanId(String parentSpanId) {
            this.parentSpanId = parentSpanId;
            return this;
        }

        public Builder startTime(long startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder baggage(Map<String, String> baggage) {
            if (baggage != null) {
                this.baggage = new HashMap<>(baggage);
            }
            return this;
        }

        public Builder addBaggage(String key, String value) {
            this.baggage.put(key, value);
            return this;
        }

        public TraceContext build() {
            return new TraceContext(this);
        }
    }

    @Override
    public String toString() {
        return String.format("TraceContext{traceId='%s', spanId='%s', parentSpanId='%s'}", 
                traceId, spanId, parentSpanId);
    }
}
