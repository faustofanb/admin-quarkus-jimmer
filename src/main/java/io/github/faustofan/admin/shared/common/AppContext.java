package io.github.faustofan.admin.shared.common;

import java.util.Locale;
import java.util.Optional;

/**
 * 应用上下文
 * <p>
 * 封装请求级别的上下文信息，用于异步任务透传。
 * 该类是不可变的，线程安全。
 *
 * <h3>包含信息：</h3>
 * <ul>
 *   <li>用户信息（userId, username）</li>
 *   <li>租户信息（tenantId）</li>
 *   <li>安全信息（roles, permissions）</li>
 *   <li>请求信息（requestId, clientIp）</li>
 *   <li>本地化信息（locale）</li>
 * </ul>
 */
public final class AppContext {

    /**
     * 用户ID
     */
    private final Long userId;

    /**
     * 用户名
     */
    private final String username;

    /**
     * 租户ID
     */
    private final Long tenantId;

    /**
     * 请求ID（用于链路追踪）
     */
    private final String requestId;

    /**
     * 客户端IP
     */
    private final String clientIp;

    /**
     * 请求URI
     */
    private final String requestUri;

    /**
     * 请求方法
     */
    private final String requestMethod;

    /**
     * 本地化信息
     */
    private final Locale locale;

    /**
     * 用户角色（逗号分隔）
     */
    private final String roles;

    /**
     * 创建时间戳
     */
    private final long createdAt;

    private AppContext(Builder builder) {
        this.userId = builder.userId;
        this.username = builder.username;
        this.tenantId = builder.tenantId;
        this.requestId = builder.requestId;
        this.clientIp = builder.clientIp;
        this.requestUri = builder.requestUri;
        this.requestMethod = builder.requestMethod;
        this.locale = builder.locale;
        this.roles = builder.roles;
        this.createdAt = System.currentTimeMillis();
    }

    // ===========================
    // 工厂方法
    // ===========================

    /**
     * 创建空上下文
     */
    public static AppContext empty() {
        return builder().build();
    }

    /**
     * 创建构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    // ===========================
    // Getter 方法
    // ===========================

    public Optional<Long> getUserId() {
        return Optional.ofNullable(userId);
    }

    public Optional<String> getUsername() {
        return Optional.ofNullable(username);
    }

    public Optional<Long> getTenantId() {
        return Optional.ofNullable(tenantId);
    }

    public Optional<String> getRequestId() {
        return Optional.ofNullable(requestId);
    }

    public Optional<String> getClientIp() {
        return Optional.ofNullable(clientIp);
    }

    public Optional<String> getRequestUri() {
        return Optional.ofNullable(requestUri);
    }

    public Optional<String> getRequestMethod() {
        return Optional.ofNullable(requestMethod);
    }

    public Optional<Locale> getLocale() {
        return Optional.ofNullable(locale);
    }

    public Optional<String> getRoles() {
        return Optional.ofNullable(roles);
    }

    public long getCreatedAt() {
        return createdAt;
    }

    // ===========================
    // 便捷方法
    // ===========================

    /**
     * 获取用户ID（必须存在，否则抛异常）
     */
    public long requireUserId() {
        return getUserId().orElseThrow(() -> new IllegalStateException("UserId is required but not present"));
    }

    /**
     * 获取租户ID（必须存在，否则抛异常）
     */
    public long requireTenantId() {
        return getTenantId().orElseThrow(() -> new IllegalStateException("TenantId is required but not present"));
    }

    /**
     * 判断是否已认证（有用户ID）
     */
    public boolean isAuthenticated() {
        return userId != null;
    }

    /**
     * 判断上下文是否为空
     */
    public boolean isEmpty() {
        return userId == null && username == null && tenantId == null && requestId == null;
    }

    /**
     * 从当前上下文创建新的构建器（用于修改部分值）
     */
    public Builder toBuilder() {
        return new Builder()
                .userId(this.userId)
                .username(this.username)
                .tenantId(this.tenantId)
                .requestId(this.requestId)
                .clientIp(this.clientIp)
                .requestUri(this.requestUri)
                .requestMethod(this.requestMethod)
                .locale(this.locale)
                .roles(this.roles);
    }

    // ===========================
    // 构建器
    // ===========================

    /**
     * 应用上下文构建器
     */
    public static class Builder {
        private Long userId;
        private String username;
        private Long tenantId;
        private String requestId;
        private String clientIp;
        private String requestUri;
        private String requestMethod;
        private Locale locale;
        private String roles;

        private Builder() {}

        public Builder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder tenantId(Long tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder clientIp(String clientIp) {
            this.clientIp = clientIp;
            return this;
        }

        public Builder requestUri(String requestUri) {
            this.requestUri = requestUri;
            return this;
        }

        public Builder requestMethod(String requestMethod) {
            this.requestMethod = requestMethod;
            return this;
        }

        public Builder locale(Locale locale) {
            this.locale = locale;
            return this;
        }

        public Builder roles(String roles) {
            this.roles = roles;
            return this;
        }

        public AppContext build() {
            return new AppContext(this);
        }
    }

    @Override
    public String toString() {
        return "AppContext{" +
                "userId=" + userId +
                ", username='" + username + '\'' +
                ", tenantId=" + tenantId +
                ", requestId='" + requestId + '\'' +
                '}';
    }
}
