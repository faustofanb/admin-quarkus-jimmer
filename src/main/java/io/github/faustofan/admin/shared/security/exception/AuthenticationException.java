package io.github.faustofan.admin.shared.security.exception;

import io.github.faustofan.admin.shared.security.constants.SecurityErrorCode;

/**
 * 认证异常
 * <p>
 * 当用户身份认证失败时抛出此异常，包括：
 * <ul>
 *   <li>用户名或密码错误</li>
 *   <li>账号被禁用/锁定</li>
 *   <li>Token无效或过期</li>
 *   <li>验证码错误</li>
 * </ul>
 */
public class AuthenticationException extends SecurityException {

    public AuthenticationException(SecurityErrorCode errorCode) {
        super(errorCode);
    }

    public AuthenticationException(SecurityErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public AuthenticationException(SecurityErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public AuthenticationException(SecurityErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    // ===========================
    // 静态工厂方法
    // ===========================

    /**
     * 凭证错误（用户名或密码错误）
     */
    public static AuthenticationException badCredentials() {
        return new AuthenticationException(SecurityErrorCode.BAD_CREDENTIALS);
    }

    /**
     * 用户不存在
     */
    public static AuthenticationException userNotFound(String username) {
        return new AuthenticationException(
            SecurityErrorCode.USER_NOT_FOUND,
            String.format("User not found: %s", username)
        );
    }

    /**
     * 账号已禁用
     */
    public static AuthenticationException accountDisabled(String username) {
        return new AuthenticationException(
            SecurityErrorCode.ACCOUNT_DISABLED,
            String.format("Account disabled: %s", username)
        );
    }

    /**
     * 账号已锁定
     */
    public static AuthenticationException accountLocked(String username) {
        return new AuthenticationException(
            SecurityErrorCode.ACCOUNT_LOCKED,
            String.format("Account locked: %s", username)
        );
    }

    /**
     * Token无效
     */
    public static AuthenticationException invalidToken() {
        return new AuthenticationException(SecurityErrorCode.INVALID_TOKEN);
    }

    /**
     * Token已过期
     */
    public static AuthenticationException tokenExpired() {
        return new AuthenticationException(SecurityErrorCode.TOKEN_EXPIRED);
    }

    /**
     * 刷新Token无效
     */
    public static AuthenticationException invalidRefreshToken() {
        return new AuthenticationException(SecurityErrorCode.INVALID_REFRESH_TOKEN);
    }

    /**
     * 刷新Token已过期
     */
    public static AuthenticationException refreshTokenExpired() {
        return new AuthenticationException(SecurityErrorCode.REFRESH_TOKEN_EXPIRED);
    }

    /**
     * 未提供Token
     */
    public static AuthenticationException tokenMissing() {
        return new AuthenticationException(SecurityErrorCode.TOKEN_MISSING);
    }

    /**
     * 会话不存在
     */
    public static AuthenticationException sessionNotFound() {
        return new AuthenticationException(SecurityErrorCode.SESSION_NOT_FOUND);
    }

    /**
     * 会话已过期
     */
    public static AuthenticationException sessionExpired() {
        return new AuthenticationException(SecurityErrorCode.SESSION_EXPIRED);
    }

    /**
     * 登录失败次数过多
     */
    public static AuthenticationException loginAttemptsExceeded() {
        return new AuthenticationException(SecurityErrorCode.LOGIN_ATTEMPTS_EXCEEDED);
    }

    /**
     * 租户不存在
     */
    public static AuthenticationException tenantNotFound(Long tenantId) {
        return new AuthenticationException(
            SecurityErrorCode.TENANT_NOT_FOUND,
            String.format("Tenant not found: %d", tenantId)
        );
    }

    /**
     * 租户已禁用
     */
    public static AuthenticationException tenantDisabled(Long tenantId) {
        return new AuthenticationException(
            SecurityErrorCode.TENANT_DISABLED,
            String.format("Tenant disabled: %d", tenantId)
        );
    }

    /**
     * 租户已过期
     */
    public static AuthenticationException tenantExpired(Long tenantId) {
        return new AuthenticationException(
            SecurityErrorCode.TENANT_EXPIRED,
            String.format("Tenant expired: %d", tenantId)
        );
    }
}
