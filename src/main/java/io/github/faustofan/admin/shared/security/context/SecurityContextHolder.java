package io.github.faustofan.admin.shared.security.context;

/**
 * 安全上下文持有者
 * <p>
 * 使用 ThreadLocal 存储当前请求的安全上下文。
 * 在请求处理开始时设置，请求处理结束时清除。
 */
public final class SecurityContextHolder {

    private static final ThreadLocal<SecurityContext> CONTEXT_HOLDER = new ThreadLocal<>();

    private SecurityContextHolder() {
        // 禁止实例化
    }

    /**
     * 获取当前安全上下文
     * <p>
     * 如果不存在则返回匿名上下文
     */
    public static SecurityContext getContext() {
        SecurityContext context = CONTEXT_HOLDER.get();
        return context != null ? context : SecurityContext.anonymous();
    }

    /**
     * 设置安全上下文
     */
    public static void setContext(SecurityContext context) {
        if (context == null) {
            clearContext();
        } else {
            CONTEXT_HOLDER.set(context);
        }
    }

    /**
     * 清除安全上下文
     */
    public static void clearContext() {
        CONTEXT_HOLDER.remove();
    }

    /**
     * 是否已认证
     */
    public static boolean isAuthenticated() {
        return getContext().isAuthenticated();
    }

    /**
     * 获取当前用户ID
     */
    public static Long getCurrentUserId() {
        return getContext().getUserId().orElse(null);
    }

    /**
     * 获取当前用户ID（必须存在）
     */
    public static Long requireCurrentUserId() {
        return getContext().requireUserId();
    }

    /**
     * 获取当前用户名
     */
    public static String getCurrentUsername() {
        return getContext().getUsername().orElse(null);
    }

    /**
     * 获取当前租户ID
     */
    public static Long getCurrentTenantId() {
        return getContext().getTenantId().orElse(null);
    }

    /**
     * 获取当前租户ID（必须存在）
     */
    public static Long requireCurrentTenantId() {
        return getContext().requireTenantId();
    }

    /**
     * 是否拥有指定权限
     */
    public static boolean hasPermission(String permission) {
        return getContext().hasPermission(permission);
    }

    /**
     * 是否拥有指定角色
     */
    public static boolean hasRole(String role) {
        return getContext().hasRole(role);
    }

    /**
     * 是否为超级管理员
     */
    public static boolean isSuperAdmin() {
        return getContext().isSuperAdmin();
    }
}
