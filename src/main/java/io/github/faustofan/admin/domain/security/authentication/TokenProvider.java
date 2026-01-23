package io.github.faustofan.admin.domain.security.authentication;

import io.github.faustofan.admin.domain.security.valueobject.TokenClaims;
import io.github.faustofan.admin.domain.security.valueobject.TokenPair;

/**
 * Token提供者接口
 * <p>
 * 定义Token生成和解析的核心契约。
 *
 * <h3>职责：</h3>
 * <ul>
 *   <li>生成访问令牌（Access Token）</li>
 *   <li>生成刷新令牌（Refresh Token）</li>
 *   <li>解析并验证Token</li>
 *   <li>刷新Token对</li>
 * </ul>
 *
 * <h3>实现：</h3>
 * <ul>
 *   <li>JwtTokenProvider - 基于JWT的实现（默认）</li>
 * </ul>
 */
public interface TokenProvider {

    /**
     * 生成Token对（访问令牌 + 刷新令牌）
     *
     * @param claims Token声明
     * @return Token对
     */
    TokenPair generateTokenPair(TokenClaims claims);

    /**
     * 解析Token
     *
     * @param token JWT字符串
     * @return Token声明
     * @throws io.github.faustofan.admin.shared.security.exception.AuthenticationException Token无效或过期时抛出
     */
    TokenClaims parseToken(String token);

    /**
     * 刷新Token
     * <p>
     * 使用刷新令牌生成新的Token对
     *
     * @param refreshToken 刷新令牌
     * @return 新的Token对
     * @throws io.github.faustofan.admin.shared.security.exception.AuthenticationException 刷新令牌无效或过期时抛出
     */
    TokenPair refreshToken(String refreshToken);

    /**
     * 验证Token是否有效
     *
     * @param token JWT字符串
     * @return true-有效，false-无效
     */
    boolean validateToken(String token);
}
