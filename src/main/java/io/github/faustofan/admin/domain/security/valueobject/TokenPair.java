package io.github.faustofan.admin.domain.security.valueobject;

import java.time.Instant;

/**
 * Token对值对象
 * <p>
 * 包含访问令牌（accessToken）和刷新令牌（refreshToken）。
 * 用于双Token机制，实现无感刷新。
 *
 * @param accessToken            访问令牌
 * @param refreshToken           刷新令牌
 * @param accessTokenExpireTime  访问令牌过期时间
 * @param refreshTokenExpireTime 刷新令牌过期时间
 */
public record TokenPair(
    String accessToken,
    String refreshToken,
    Instant accessTokenExpireTime,
    Instant refreshTokenExpireTime
) {

    /**
     * 访问令牌是否过期
     */
    public boolean isAccessTokenExpired() {
        return accessTokenExpireTime != null && Instant.now().isAfter(accessTokenExpireTime);
    }

    /**
     * 刷新令牌是否过期
     */
    public boolean isRefreshTokenExpired() {
        return refreshTokenExpireTime != null && Instant.now().isAfter(refreshTokenExpireTime);
    }

    /**
     * 访问令牌剩余有效时间（秒）
     */
    public long accessTokenRemainingSeconds() {
        if (accessTokenExpireTime == null) {
            return 0;
        }
        long remaining = accessTokenExpireTime.getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(0, remaining);
    }

    /**
     * 刷新令牌剩余有效时间（秒）
     */
    public long refreshTokenRemainingSeconds() {
        if (refreshTokenExpireTime == null) {
            return 0;
        }
        long remaining = refreshTokenExpireTime.getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(0, remaining);
    }
}
