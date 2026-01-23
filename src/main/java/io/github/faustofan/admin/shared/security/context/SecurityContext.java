package io.github.faustofan.admin.shared.security.context;

import io.github.faustofan.admin.domain.security.valueobject.TokenClaims;

import java.util.List;
import java.util.Optional;

/**
 * 安全上下文
 * <p>
 * 封装当前请求的安全信息，包括Token声明和用户权限。
 * 通过 {@link SecurityContextHolder} 在请求范围内访问。
 */
public class SecurityContext {

    private final TokenClaims tokenClaims;
    private final List<String> permissions;
    private final String sessionId;

    private SecurityContext(TokenClaims tokenClaims, List<String> permissions, String sessionId) {
        this.tokenClaims = tokenClaims;
        this.permissions = permissions;
        this.sessionId = sessionId;
    }

    /**
     * 创建安全上下文
     */
    public static SecurityContext of(TokenClaims tokenClaims, List<String> permissions, String sessionId) {
        return new SecurityContext(tokenClaims, permissions, sessionId);
    }

    /**
     * 创建安全上下文（无权限列表）
     */
    public static SecurityContext of(TokenClaims tokenClaims) {
        return new SecurityContext(tokenClaims, List.of(), null);
    }

    /**
     * 创建空的安全上下文（匿名用户）
     */
    public static SecurityContext anonymous() {
        return new SecurityContext(null, List.of(), null);
    }

    // ===========================
    // Getter 方法
    // ===========================

    public Optional<TokenClaims> getTokenClaims() {
        return Optional.ofNullable(tokenClaims);
    }

    public List<String> getPermissions() {
        return permissions != null ? permissions : List.of();
    }

    public Optional<String> getSessionId() {
        return Optional.ofNullable(sessionId);
    }

    // ===========================
    // 便捷方法
    // ===========================

    /**
     * 是否已认证
     */
    public boolean isAuthenticated() {
        return tokenClaims != null && tokenClaims.userId() != null;
    }

    /**
     * 获取用户ID
     */
    public Optional<Long> getUserId() {
        return getTokenClaims().map(TokenClaims::userId);
    }

    /**
     * 获取用户ID（必须存在）
     */
    public Long requireUserId() {
        return getUserId().orElseThrow(() -> new IllegalStateException("User ID is required but not present"));
    }

    /**
     * 获取用户名
     */
    public Optional<String> getUsername() {
        return getTokenClaims().map(TokenClaims::username);
    }

    /**
     * 获取租户ID
     */
    public Optional<Long> getTenantId() {
        return getTokenClaims().map(TokenClaims::tenantId);
    }

    /**
     * 获取租户ID（必须存在）
     */
    public Long requireTenantId() {
        return getTenantId().orElseThrow(() -> new IllegalStateException("Tenant ID is required but not present"));
    }

    /**
     * 获取部门ID
     */
    public Optional<Long> getDeptId() {
        return getTokenClaims().map(TokenClaims::deptId);
    }

    /**
     * 获取角色列表
     */
    public List<String> getRoles() {
        return getTokenClaims().map(TokenClaims::roles).orElse(List.of());
    }

    /**
     * 是否拥有指定权限
     */
    public boolean hasPermission(String permission) {
        if (isSuperAdmin()) {
            return true;
        }
        return getPermissions().contains(permission);
    }

    /**
     * 是否拥有指定角色
     */
    public boolean hasRole(String role) {
        return getRoles().contains(role);
    }

    /**
     * 是否为超级管理员
     */
    public boolean isSuperAdmin() {
        return getRoles().contains("super_admin");
    }
}
