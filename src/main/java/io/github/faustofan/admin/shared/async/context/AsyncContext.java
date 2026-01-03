package io.github.faustofan.admin.shared.async.context;

import io.github.faustofan.admin.shared.common.AppContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 异步上下文
 * <p>
 * 封装需要在异步任务中透传的完整上下文信息：
 * <ul>
 *   <li>MDC诊断上下文</li>
 *   <li>应用上下文（用户、租户、请求信息等）</li>
 * </ul>
 * <p>
 * 该类是不可变的，线程安全。
 */
public final class AsyncContext {

    /**
     * MDC上下文快照
     */
    private final Map<String, String> mdcContext;

    /**
     * 应用上下文
     */
    private final AppContext appContext;

    /**
     * 创建时间戳
     */
    private final long createdAt;

    private AsyncContext(Map<String, String> mdcContext, AppContext appContext) {
        this.mdcContext = mdcContext != null
                ? Collections.unmodifiableMap(new HashMap<>(mdcContext))
                : Collections.emptyMap();
        this.appContext = appContext != null ? appContext : AppContext.empty();
        this.createdAt = System.currentTimeMillis();
    }

    // ===========================
    // 工厂方法
    // ===========================

    /**
     * 创建空的异步上下文
     */
    public static AsyncContext empty() {
        return new AsyncContext(null, null);
    }

    /**
     * 仅从MDC上下文创建
     *
     * @param mdcContext MDC上下文Map
     */
    public static AsyncContext fromMdc(Map<String, String> mdcContext) {
        return new AsyncContext(mdcContext, null);
    }

    /**
     * 仅从应用上下文创建
     *
     * @param appContext 应用上下文
     */
    public static AsyncContext fromApp(AppContext appContext) {
        return new AsyncContext(null, appContext);
    }

    /**
     * 从完整上下文创建
     *
     * @param mdcContext MDC上下文Map
     * @param appContext 应用上下文
     */
    public static AsyncContext of(Map<String, String> mdcContext, AppContext appContext) {
        return new AsyncContext(mdcContext, appContext);
    }

    // ===========================
    // Getter 方法
    // ===========================

    /**
     * 获取MDC上下文（不可变）
     */
    public Map<String, String> getMdcContext() {
        return mdcContext;
    }

    /**
     * 获取应用上下文
     */
    public AppContext getAppContext() {
        return appContext;
    }

    /**
     * 获取MDC值
     *
     * @param key MDC key
     * @return Optional值
     */
    public Optional<String> getMdcValue(String key) {
        return Optional.ofNullable(mdcContext.get(key));
    }

    /**
     * 获取上下文创建时间
     */
    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * 判断上下文是否为空
     */
    public boolean isEmpty() {
        return mdcContext.isEmpty() && appContext.isEmpty();
    }

    // ===========================
    // 便捷方法（代理到AppContext）
    // ===========================

    /**
     * 获取用户ID
     */
    public Optional<Long> getUserId() {
        return appContext.getUserId();
    }

    /**
     * 获取租户ID
     */
    public Optional<Long> getTenantId() {
        return appContext.getTenantId();
    }

    /**
     * 获取请求ID
     */
    public Optional<String> getRequestId() {
        return appContext.getRequestId();
    }

    /**
     * 判断是否已认证
     */
    public boolean isAuthenticated() {
        return appContext.isAuthenticated();
    }

    // ===========================
    // 构建器
    // ===========================

    /**
     * 创建构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 上下文构建器
     */
    public static class Builder {
        private Map<String, String> mdcContext = new HashMap<>();
        private AppContext appContext;

        private Builder() {}

        /**
         * 设置MDC上下文
         */
        public Builder mdcContext(Map<String, String> mdcContext) {
            if (mdcContext != null) {
                this.mdcContext.putAll(mdcContext);
            }
            return this;
        }

        /**
         * 添加MDC键值
         */
        public Builder mdc(String key, String value) {
            if (key != null && value != null) {
                this.mdcContext.put(key, value);
            }
            return this;
        }

        /**
         * 设置应用上下文
         */
        public Builder appContext(AppContext appContext) {
            this.appContext = appContext;
            return this;
        }

        /**
         * 构建上下文
         */
        public AsyncContext build() {
            return new AsyncContext(mdcContext, appContext);
        }
    }

    @Override
    public String toString() {
        return "AsyncContext{" +
                "mdcKeys=" + mdcContext.keySet() +
                ", appContext=" + appContext +
                ", createdAt=" + createdAt +
                '}';
    }
}
