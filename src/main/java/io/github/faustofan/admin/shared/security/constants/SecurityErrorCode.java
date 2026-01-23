package io.github.faustofan.admin.shared.security.constants;

/**
 * 安全模块错误码枚举
 * <p>
 * 定义认证、授权相关的错误码，便于前端统一处理。
 * 错误码规则：A开头表示认证（Authentication），Z开头表示授权（Authorization）
 */
public enum SecurityErrorCode {

    // ===========================
    // 认证相关错误码 (A开头)
    // ===========================
    /** 用户名或密码错误 */
    BAD_CREDENTIALS("A0001", "用户名或密码错误"),

    /** 用户账号已禁用 */
    ACCOUNT_DISABLED("A0002", "账号已被禁用"),

    /** 用户账号已锁定 */
    ACCOUNT_LOCKED("A0003", "账号已被锁定"),

    /** 账号已过期 */
    ACCOUNT_EXPIRED("A0004", "账号已过期"),

    /** 凭证已过期（密码过期，预留） */
    CREDENTIALS_EXPIRED("A0005", "密码已过期，请修改密码"),

    /** 用户不存在 */
    USER_NOT_FOUND("A0006", "用户不存在"),

    /** Token无效 */
    INVALID_TOKEN("A0010", "无效的访问令牌"),

    /** Token已过期 */
    TOKEN_EXPIRED("A0011", "访问令牌已过期"),

    /** 刷新Token无效 */
    INVALID_REFRESH_TOKEN("A0012", "无效的刷新令牌"),

    /** 刷新Token已过期 */
    REFRESH_TOKEN_EXPIRED("A0013", "刷新令牌已过期"),

    /** 未提供Token */
    TOKEN_MISSING("A0014", "请先登录"),

    /** 验证码错误（预留） */
    CAPTCHA_ERROR("A0020", "验证码错误"),

    /** 验证码已过期（预留） */
    CAPTCHA_EXPIRED("A0021", "验证码已过期"),

    /** 登录失败次数过多 */
    LOGIN_ATTEMPTS_EXCEEDED("A0030", "登录失败次数过多，请稍后再试"),

    /** 会话不存在 */
    SESSION_NOT_FOUND("A0040", "会话不存在"),

    /** 会话已过期 */
    SESSION_EXPIRED("A0041", "会话已过期，请重新登录"),

    /** 异地登录被挤下线 */
    CONCURRENT_LOGIN("A0042", "您的账号在其他设备登录"),

    // ===========================
    // 租户相关错误码 (T开头)
    // ===========================
    /** 租户不存在 */
    TENANT_NOT_FOUND("T0001", "租户不存在"),

    /** 租户已禁用 */
    TENANT_DISABLED("T0002", "租户已被禁用"),

    /** 租户已过期 */
    TENANT_EXPIRED("T0003", "租户服务已到期"),

    /** 租户账号数量超限 */
    TENANT_USER_LIMIT("T0004", "租户账号数量已达上限"),

    // ===========================
    // 授权相关错误码 (Z开头)
    // ===========================
    /** 无访问权限 */
    ACCESS_DENIED("Z0001", "无访问权限"),

    /** 缺少必要权限 */
    PERMISSION_DENIED("Z0002", "您没有该操作的权限"),

    /** 缺少必要角色 */
    ROLE_DENIED("Z0003", "您没有该角色的权限"),

    /** 数据权限不足 */
    DATA_PERMISSION_DENIED("Z0010", "您没有该数据的访问权限"),

    // ===========================
    // 通用错误
    // ===========================
    /** 未知认证错误 */
    UNKNOWN_AUTH_ERROR("A9999", "认证失败，请稍后重试"),

    /** 未知授权错误 */
    UNKNOWN_AUTHZ_ERROR("Z9999", "授权失败，请稍后重试");

    private final String code;
    private final String message;

    SecurityErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s", code, message);
    }
}
