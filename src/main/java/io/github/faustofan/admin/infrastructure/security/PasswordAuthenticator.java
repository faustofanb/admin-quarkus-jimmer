package io.github.faustofan.admin.infrastructure.security;

import io.github.faustofan.admin.domain.constants.CommonStatus;
import io.github.faustofan.admin.domain.entity.*;
import io.github.faustofan.admin.domain.security.authentication.AuthenticationResult;
import io.github.faustofan.admin.domain.security.authentication.Authenticator;
import io.github.faustofan.admin.domain.security.authentication.PasswordEncoder;
import io.github.faustofan.admin.domain.security.valueobject.Credential;
import io.github.faustofan.admin.shared.security.constants.SecurityConstants;
import io.github.faustofan.admin.shared.security.exception.AuthenticationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.babyfish.jimmer.sql.JSqlClient;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户名密码认证器实现
 * <p>
 * 基于用户名密码进行身份认证，验证用户凭证、账号状态、租户状态等。
 */
@ApplicationScoped
public class PasswordAuthenticator implements Authenticator {

    private static final Logger LOG = Logger.getLogger(PasswordAuthenticator.class);

    private final JSqlClient sqlClient;
    private final PasswordEncoder passwordEncoder;

    @Inject
    public PasswordAuthenticator(JSqlClient sqlClient, PasswordEncoder passwordEncoder) {
        this.sqlClient = sqlClient;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public AuthenticationResult authenticate(Credential credential) {
        LOG.debugv("Authenticating user: {0}", credential.username());

        // 1. 验证凭证完整性
        if (!credential.isComplete()) {
            throw AuthenticationException.badCredentials();
        }

        // 2. 查询用户
        SystemUserTable user = SystemUserTable.$;
        SystemUser systemUser = sqlClient.createQuery(user)
            .where(user.username().eq(credential.username()))
            .where(user.deleted().eq(0))
            .select(user.fetch(
                SystemUserFetcher.$
                    .allScalarFields()
                    .userRoles(
                        SystemRoleFetcher.$
                            .allScalarFields()
                            .roleMenus(
                                SystemMenuFetcher.$
                                    .permission()
                                    .status()
                            )
                    )
            ))
            .fetchOneOrNull();

        if (systemUser == null) {
            LOG.warnv("User not found: {0}", credential.username());
            throw AuthenticationException.badCredentials();
        }

        // 3. 验证密码
        if (!passwordEncoder.matches(credential.password(), systemUser.password())) {
            LOG.warnv("Invalid password for user: {0}", credential.username());
            throw AuthenticationException.badCredentials();
        }

        // 4. 验证账号状态
        if (systemUser.status() != CommonStatus.NORMAL) {
            LOG.warnv("User account disabled: {0}", credential.username());
            throw AuthenticationException.accountDisabled(credential.username());
        }

        // 5. 验证租户（如果指定了租户）
        if (credential.hasTenant() && systemUser.tenantId() != credential.tenantId()) {
            LOG.warnv("Tenant mismatch for user: {0}", credential.username());
            throw AuthenticationException.badCredentials();
        }

        // 6. 获取角色和权限
        List<String> roles = extractRoles(systemUser);
        List<String> permissions = extractPermissions(systemUser);

        LOG.debugv("User authenticated: {0}, roles={1}, permissions count={2}",
            credential.username(), roles, permissions.size());

        // 7. 构建认证结果
        return AuthenticationResult.builder()
            .userId(systemUser.id())
            .username(systemUser.username())
            .nickname(systemUser.nickname())
            .avatar(null) // SystemUser暂无avatar字段
            .tenantId(systemUser.tenantId())
            .deptId(null) // SystemUser暂无直接deptId字段
            .roles(roles)
            .permissions(permissions)
            .build();
    }

    @Override
    public String supportedType() {
        return SecurityConstants.AuthType.PASSWORD;
    }

    // ===========================
    // 私有辅助方法
    // ===========================

    /**
     * 提取用户角色编码列表
     */
    private List<String> extractRoles(SystemUser user) {
        if (user.userRoles() == null || user.userRoles().isEmpty()) {
            return List.of();
        }
        return user.userRoles().stream()
            .filter(role -> role.status() == CommonStatus.NORMAL)
            .map(SystemRole::code)
            .collect(Collectors.toList());
    }

    /**
     * 提取用户权限标识列表
     */
    private List<String> extractPermissions(SystemUser user) {
        if (user.userRoles() == null || user.userRoles().isEmpty()) {
            return List.of();
        }
        return user.userRoles().stream()
            .filter(role -> role.status() == CommonStatus.NORMAL)
            .flatMap(role -> {
                if (role.roleMenus() == null) {
                    return java.util.stream.Stream.empty();
                }
                return role.roleMenus().stream();
            })
            .filter(menu -> menu.status() == CommonStatus.NORMAL)
            .map(SystemMenu::permission)
            .filter(perm -> perm != null && !perm.isBlank())
            .distinct()
            .collect(Collectors.toList());
    }
}
