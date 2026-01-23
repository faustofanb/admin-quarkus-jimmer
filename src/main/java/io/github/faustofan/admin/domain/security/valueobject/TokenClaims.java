package io.github.faustofan.admin.domain.security.valueobject;

import java.time.Instant;
import java.util.List;

/**
 * Token声明值对象
 * <p>
 * 封装JWT Token中携带的声明信息，用于后续的权限校验。
 *
 * @param userId    用户ID
 * @param username  用户名
 * @param tenantId  租户ID
 * @param deptId    部门ID
 * @param tokenType Token类型（access/refresh）
 * @param roles     角色编码列表
 * @param issuedAt  签发时间
 * @param expiresAt 过期时间
 */
public record TokenClaims(
    Long userId,
    String username,
    Long tenantId,
    Long deptId,
    String tokenType,
    List<String> roles,
    Instant issuedAt,
    Instant expiresAt
) {

    /**
     * Token是否过期
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * 是否为访问令牌
     */
    public boolean isAccessToken() {
        return "access".equals(tokenType);
    }

    /**
     * 是否为刷新令牌
     */
    public boolean isRefreshToken() {
        return "refresh".equals(tokenType);
    }

    /**
     * 剩余有效时间（秒）
     */
    public long remainingSeconds() {
        if (expiresAt == null) {
            return 0;
        }
        long remaining = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(0, remaining);
    }

    /**
     * 创建访问令牌声明的Builder
     */
    public static Builder accessToken() {
        return new Builder().tokenType("access");
    }

    /**
     * 创建刷新令牌声明的Builder
     */
    public static Builder refreshToken() {
        return new Builder().tokenType("refresh");
    }

    /**
     * Builder模式构建TokenClaims
     */
    public static class Builder {
        private Long userId;
        private String username;
        private Long tenantId;
        private Long deptId;
        private String tokenType;
        private List<String> roles;
        private Instant issuedAt;
        private Instant expiresAt;

        public Builder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder tenantId(Long tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder deptId(Long deptId) {
            this.deptId = deptId;
            return this;
        }

        public Builder tokenType(String tokenType) {
            this.tokenType = tokenType;
            return this;
        }

        public Builder roles(List<String> roles) {
            this.roles = roles;
            return this;
        }

        public Builder issuedAt(Instant issuedAt) {
            this.issuedAt = issuedAt;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public TokenClaims build() {
            return new TokenClaims(userId, username, tenantId, deptId, tokenType, roles, issuedAt, expiresAt);
        }
    }
}
