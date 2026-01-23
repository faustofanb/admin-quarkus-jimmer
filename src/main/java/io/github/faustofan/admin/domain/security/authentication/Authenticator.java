package io.github.faustofan.admin.domain.security.authentication;

import io.github.faustofan.admin.domain.security.valueobject.Credential;

/**
 * 认证器接口
 * <p>
 * 定义用户身份认证的核心契约。支持多种认证方式（用户名密码、OAuth2、LDAP等）。
 * 每种认证方式实现各自的认证器，通过 {@link #supportedType()} 区分。
 *
 * <h3>实现要求：</h3>
 * <ul>
 *   <li>验证用户凭证的有效性</li>
 *   <li>验证用户账号状态（是否启用、是否锁定）</li>
 *   <li>验证租户状态（是否有效、是否过期）</li>
 *   <li>返回完整的认证结果</li>
 * </ul>
 *
 * <h3>扩展点：</h3>
 * <ul>
 *   <li>PasswordAuthenticator - 用户名密码认证（默认）</li>
 *   <li>SmsAuthenticator - 短信验证码认证（预留）</li>
 *   <li>OAuth2Authenticator - 第三方OAuth2认证（预留）</li>
 *   <li>LdapAuthenticator - LDAP认证（预留）</li>
 * </ul>
 */
public interface Authenticator {

    /**
     * 执行身份认证
     *
     * @param credential 用户凭证
     * @return 认证结果（包含用户信息和权限）
     * @throws io.github.faustofan.admin.shared.security.exception.AuthenticationException 认证失败时抛出
     */
    AuthenticationResult authenticate(Credential credential);

    /**
     * 支持的认证类型
     * <p>
     * 如：password, sms, oauth2, ldap
     */
    String supportedType();

    /**
     * 是否支持指定的凭证类型
     */
    default boolean supports(Credential credential) {
        return supportedType().equals(credential.authType());
    }
}
