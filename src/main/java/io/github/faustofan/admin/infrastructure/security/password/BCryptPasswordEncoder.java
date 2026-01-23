package io.github.faustofan.admin.infrastructure.security.password;

import io.github.faustofan.admin.domain.security.authentication.PasswordEncoder;
import io.github.faustofan.admin.shared.security.config.SecurityConfig;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * BCrypt密码编码器实现
 * <p>
 * 使用BCrypt算法对密码进行哈希加密，这是目前最安全的密码哈希算法之一。
 *
 * <h3>特性：</h3>
 * <ul>
 *   <li>单向哈希，无法反向解密</li>
 *   <li>内置盐值，自动生成</li>
 *   <li>可配置的工作因子（强度）</li>
 * </ul>
 */
@ApplicationScoped
public class BCryptPasswordEncoder implements PasswordEncoder {

    private final int strength;

    @Inject
    public BCryptPasswordEncoder(SecurityConfig securityConfig) {
        this.strength = securityConfig.password().bcryptStrength();
    }

    /**
     * 默认构造器（用于测试）
     */
    public BCryptPasswordEncoder() {
        this.strength = 10;
    }

    @Override
    public String encode(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        return BcryptUtil.bcryptHash(rawPassword, strength);
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        return BcryptUtil.matches(rawPassword, encodedPassword);
    }
}
