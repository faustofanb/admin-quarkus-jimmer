package io.github.faustofan.admin.infrastructure.security;

import io.github.faustofan.admin.domain.constants.CommonStatus;
import io.github.faustofan.admin.domain.entity.*;
import io.github.faustofan.admin.domain.security.authorization.PermissionChecker;
import io.github.faustofan.admin.shared.cache.CacheFacade;
import io.github.faustofan.admin.shared.security.constants.SecurityConstants;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.babyfish.jimmer.sql.JSqlClient;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 基于Jimmer的权限检查器实现
 * <p>
 * 查询数据库获取用户的角色和权限，并支持缓存。
 */
@ApplicationScoped
public class JimmerPermissionChecker implements PermissionChecker {

    private static final Logger LOG = Logger.getLogger(JimmerPermissionChecker.class);

    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private final JSqlClient sqlClient;
    private final CacheFacade cacheFacade;

    @Inject
    public JimmerPermissionChecker(JSqlClient sqlClient, CacheFacade cacheFacade) {
        this.sqlClient = sqlClient;
        this.cacheFacade = cacheFacade;
    }

    @Override
    public boolean hasPermission(Long userId, String permission) {
        if (isSuperAdmin(userId)) {
            return true;
        }
        List<String> permissions = getUserPermissions(userId);
        return permissions.contains(permission) ||
            permissions.stream().anyMatch(p -> matchPermission(p, permission));
    }

    @Override
    public boolean hasAnyPermission(Long userId, List<String> permissions) {
        if (isSuperAdmin(userId)) {
            return true;
        }
        List<String> userPermissions = getUserPermissions(userId);
        return permissions.stream().anyMatch(p ->
            userPermissions.contains(p) ||
            userPermissions.stream().anyMatch(up -> matchPermission(up, p))
        );
    }

    @Override
    public boolean hasAllPermissions(Long userId, List<String> permissions) {
        if (isSuperAdmin(userId)) {
            return true;
        }
        List<String> userPermissions = getUserPermissions(userId);
        return permissions.stream().allMatch(p ->
            userPermissions.contains(p) ||
            userPermissions.stream().anyMatch(up -> matchPermission(up, p))
        );
    }

    @Override
    public boolean hasRole(Long userId, String roleCode) {
        List<String> roles = getUserRoles(userId);
        return roles.contains(roleCode);
    }

    @Override
    public boolean hasAnyRole(Long userId, List<String> roleCodes) {
        List<String> roles = getUserRoles(userId);
        return roleCodes.stream().anyMatch(roles::contains);
    }

    @Override
    public boolean isSuperAdmin(Long userId) {
        List<String> roles = getUserRoles(userId);
        return roles.contains(SecurityConstants.Role.SUPER_ADMIN);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getUserPermissions(Long userId) {
        String cacheKey = SecurityConstants.CacheKey.PERMISSION + userId;
        return cacheFacade.getOrLoad(
            cacheKey,
            List.class,
            () -> loadUserPermissions(userId),
            CACHE_TTL
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getUserRoles(Long userId) {
        String cacheKey = SecurityConstants.CacheKey.ROLE + userId;
        return cacheFacade.getOrLoad(
            cacheKey,
            List.class,
            () -> loadUserRoles(userId),
            CACHE_TTL
        );
    }

    // ===========================
    // 私有辅助方法
    // ===========================

    /**
     * 从数据库加载用户权限
     */
    private List<String> loadUserPermissions(Long userId) {
        LOG.debugv("Loading permissions for user: {0}", userId);

        SystemUserTable user = SystemUserTable.$;
        SystemUser systemUser = sqlClient.createQuery(user)
            .where(user.id().eq(userId))
            .where(user.deleted().eq(0))
            .select(user.fetch(
                SystemUserFetcher.$
                    .userRoles(
                        SystemRoleFetcher.$
                            .code()
                            .status()
                            .roleMenus(
                                SystemMenuFetcher.$
                                    .permission()
                                    .status()
                            )
                    )
            ))
            .fetchOneOrNull();

        if (systemUser == null || systemUser.userRoles() == null) {
            return List.of();
        }

        return systemUser.userRoles().stream()
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

    /**
     * 从数据库加载用户角色
     */
    private List<String> loadUserRoles(Long userId) {
        LOG.debugv("Loading roles for user: {0}", userId);

        SystemUserTable user = SystemUserTable.$;
        SystemUser systemUser = sqlClient.createQuery(user)
            .where(user.id().eq(userId))
            .where(user.deleted().eq(0))
            .select(user.fetch(
                SystemUserFetcher.$
                    .userRoles(
                        SystemRoleFetcher.$
                            .code()
                            .status()
                    )
            ))
            .fetchOneOrNull();

        if (systemUser == null || systemUser.userRoles() == null) {
            return List.of();
        }

        return systemUser.userRoles().stream()
            .filter(role -> role.status() == CommonStatus.NORMAL)
            .map(SystemRole::code)
            .collect(Collectors.toList());
    }

    /**
     * 权限通配符匹配
     */
    private boolean matchPermission(String pattern, String permission) {
        if (pattern.equals(permission)) {
            return true;
        }
        if ("*".equals(pattern) || "*:*:*".equals(pattern)) {
            return true;
        }
        if (pattern.contains("*")) {
            String regex = pattern.replace("*", ".*");
            return permission.matches(regex);
        }
        return false;
    }
}
