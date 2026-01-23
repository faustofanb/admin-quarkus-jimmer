package io.github.faustofan.admin.domain.security.authentication;

/**
 * 密码编码器接口
 * <p>
 * 定义密码加密和验证的核心契约。
 *
 * <h3>实现：</h3>
 * <ul>
 *   <li>BCryptPasswordEncoder - 基于BCrypt的实现（默认）</li>
 * </ul>
 */
public interface PasswordEncoder {

    /**
     * 对原始密码进行加密
     *
     * @param rawPassword 原始密码
     * @return 加密后的密码
     */
    String encode(String rawPassword);

    /**
     * 验证原始密码是否与加密密码匹配
     *
     * @param rawPassword     原始密码
     * @param encodedPassword 加密后的密码
     * @return true-匹配，false-不匹配
     */
    boolean matches(String rawPassword, String encodedPassword);
}
