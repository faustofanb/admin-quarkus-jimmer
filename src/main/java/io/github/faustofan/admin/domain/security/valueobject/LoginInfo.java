package io.github.faustofan.admin.domain.security.valueobject;

import java.time.Instant;
import java.util.List;

/**
 * 登录信息值对象
 * <p>
 * 封装登录成功后返回给客户端的完整信息。
 *
 * @param userId      用户ID
 * @param username    用户名
 * @param nickname    昵称
 * @param avatar      头像
 * @param tenantId    租户ID
 * @param deptId      部门ID
 * @param roles       角色编码列表
 * @param permissions 权限标识列表
 * @param tokenPair   Token对
 * @param loginTime   登录时间
 */
public record LoginInfo(
    Long userId,
    String username,
    String nickname,
    String avatar,
    Long tenantId,
    Long deptId,
    List<String> roles,
    List<String> permissions,
    TokenPair tokenPair,
    Instant loginTime
) {

    /**
     * 判断是否为超级管理员
     */
    public boolean isSuperAdmin() {
        return roles != null && roles.contains("super_admin");
    }

    /**
     * 判断是否拥有指定权限
     */
    public boolean hasPermission(String permission) {
        if (isSuperAdmin()) {
            return true;
        }
        return permissions != null && permissions.contains(permission);
    }

    /**
     * 判断是否拥有任一权限
     */
    public boolean hasAnyPermission(String... perms) {
        if (isSuperAdmin()) {
            return true;
        }
        if (permissions == null || perms == null) {
            return false;
        }
        for (String perm : perms) {
            if (permissions.contains(perm)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否拥有指定角色
     */
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

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
        private TokenPair tokenPair;
        private Instant loginTime;

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

        public Builder tokenPair(TokenPair tokenPair) {
            this.tokenPair = tokenPair;
            return this;
        }

        public Builder loginTime(Instant loginTime) {
            this.loginTime = loginTime;
            return this;
        }

        public LoginInfo build() {
            return new LoginInfo(userId, username, nickname, avatar, tenantId, deptId,
                roles, permissions, tokenPair, loginTime != null ? loginTime : Instant.now());
        }
    }
}
