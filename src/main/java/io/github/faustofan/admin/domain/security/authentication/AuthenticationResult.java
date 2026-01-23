package io.github.faustofan.admin.domain.security.authentication;

import java.util.List;

/**
 * 认证结果
 * <p>
 * 封装认证器返回的认证结果信息。
 *
 * @param userId      用户ID
 * @param username    用户名
 * @param nickname    昵称
 * @param avatar      头像
 * @param tenantId    租户ID
 * @param deptId      部门ID
 * @param roles       角色编码列表
 * @param permissions 权限标识列表
 */
public record AuthenticationResult(
    Long userId,
    String username,
    String nickname,
    String avatar,
    Long tenantId,
    Long deptId,
    List<String> roles,
    List<String> permissions
) {

    /**
     * Builder模式
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long userId;
        private String username;
        private String nickname;
        private String avatar;
        private Long tenantId;
        private Long deptId;
        private List<String> roles;
        private List<String> permissions;

        public Builder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder nickname(String nickname) {
            this.nickname = nickname;
            return this;
        }

        public Builder avatar(String avatar) {
            this.avatar = avatar;
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

        public Builder roles(List<String> roles) {
            this.roles = roles;
            return this;
        }

        public Builder permissions(List<String> permissions) {
            this.permissions = permissions;
            return this;
        }

        public AuthenticationResult build() {
            return new AuthenticationResult(
                userId, username, nickname, avatar,
                tenantId, deptId, roles, permissions
            );
        }
    }
}
