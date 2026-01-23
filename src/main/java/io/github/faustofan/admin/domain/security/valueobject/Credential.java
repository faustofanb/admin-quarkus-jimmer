package io.github.faustofan.admin.domain.security.valueobject;

/**
 * 登录凭证值对象
 * <p>
 * 封装用户登录时提交的凭证信息，支持多种认证方式扩展。
 *
 * @param username    用户名
 * @param password    密码
 * @param captchaCode 验证码（预留扩展）
 * @param captchaKey  验证码Key（预留扩展）
 * @param tenantId    租户ID（多租户场景）
 * @param authType    认证类型（预留扩展：password/sms/oauth2等）
 */
public record Credential(
    String username,
    String password,
    String captchaCode,
    String captchaKey,
    Long tenantId,
    String authType
) {

    /**
     * 创建用户名密码凭证
     */
    public static Credential ofPassword(String username, String password, Long tenantId) {
        return new Credential(username, password, null, null, tenantId, "password");
    }

    /**
     * 创建用户名密码凭证（默认租户）
     */
    public static Credential ofPassword(String username, String password) {
        return ofPassword(username, password, null);
    }

    /**
     * 验证凭证是否完整（用户名密码登录）
     */
    public boolean isComplete() {
        return username != null && !username.isBlank()
            && password != null && !password.isBlank();
    }

    /**
     * 是否包含验证码
     */
    public boolean hasCaptcha() {
        return captchaCode != null && !captchaCode.isBlank()
            && captchaKey != null && !captchaKey.isBlank();
    }

    /**
     * 是否指定了租户
     */
    public boolean hasTenant() {
        return tenantId != null;
    }
}
