package io.github.faustofan.admin.shared.security.exception;

import io.github.faustofan.admin.shared.security.constants.SecurityErrorCode;

/**
 * 授权异常
 * <p>
 * 当用户无权访问资源时抛出此异常，包括：
 * <ul>
 *   <li>缺少必要权限</li>
 *   <li>缺少必要角色</li>
 *   <li>数据权限不足</li>
 * </ul>
 */
public class AuthorizationException extends SecurityException {

    /** 所需的权限标识 */
    private final String requiredPermission;

    public AuthorizationException(SecurityErrorCode errorCode) {
        super(errorCode);
        this.requiredPermission = null;
    }

    public AuthorizationException(SecurityErrorCode errorCode, String message) {
        super(errorCode, message);
        this.requiredPermission = null;
    }

    public AuthorizationException(SecurityErrorCode errorCode, String message, String requiredPermission) {
        super(errorCode, message);
        this.requiredPermission = requiredPermission;
    }

    /**
     * 获取所需的权限标识
     */
    public String getRequiredPermission() {
        return requiredPermission;
    }

    // ===========================
    // 静态工厂方法
    // ===========================

    /**
     * 访问被拒绝
     */
    public static AuthorizationException accessDenied() {
        return new AuthorizationException(SecurityErrorCode.ACCESS_DENIED);
    }

    /**
     * 缺少权限
     */
    public static AuthorizationException permissionDenied(String permission) {
        return new AuthorizationException(
            SecurityErrorCode.PERMISSION_DENIED,
            String.format("Permission denied: %s", permission),
            permission
        );
    }

    /**
     * 缺少角色
     */
    public static AuthorizationException roleDenied(String role) {
        return new AuthorizationException(
            SecurityErrorCode.ROLE_DENIED,
            String.format("Role denied: %s", role),
            role
        );
    }

    /**
     * 数据权限不足
     */
    public static AuthorizationException dataPermissionDenied() {
        return new AuthorizationException(SecurityErrorCode.DATA_PERMISSION_DENIED);
    }

    /**
     * 数据权限不足（带详情）
     */
    public static AuthorizationException dataPermissionDenied(String resource, Object resourceId) {
        return new AuthorizationException(
            SecurityErrorCode.DATA_PERMISSION_DENIED,
            String.format("Data permission denied: %s[%s]", resource, resourceId)
        );
    }
}
