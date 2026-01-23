package io.github.faustofan.admin.domain.security.authorization;

import java.util.List;

/**
 * 权限检查器接口
 * <p>
 * 定义权限校验的核心契约，支持多种权限检查策略。
 *
 * <h3>支持的检查类型：</h3>
 * <ul>
 *   <li>单一权限检查</li>
 *   <li>多权限与（AND）检查</li>
 *   <li>多权限或（OR）检查</li>
 *   <li>角色检查</li>
 * </ul>
 *
 * <h3>实现：</h3>
 * <ul>
 *   <li>JimmerPermissionChecker - 基于Jimmer的权限检查器（默认）</li>
 * </ul>
 */
public interface PermissionChecker {

    /**
     * 检查是否拥有指定权限
     *
     * @param userId     用户ID
     * @param permission 权限标识（如 system:user:create）
     * @return true-拥有权限，false-无权限
     */
    boolean hasPermission(Long userId, String permission);

    /**
     * 检查是否拥有任一权限（OR逻辑）
     *
     * @param userId      用户ID
     * @param permissions 权限列表
     * @return true-拥有任一权限
     */
    boolean hasAnyPermission(Long userId, List<String> permissions);

    /**
     * 检查是否拥有所有权限（AND逻辑）
     *
     * @param userId      用户ID
     * @param permissions 权限列表
     * @return true-拥有所有权限
     */
    boolean hasAllPermissions(Long userId, List<String> permissions);

    /**
     * 检查是否拥有指定角色
     *
     * @param userId   用户ID
     * @param roleCode 角色编码
     * @return true-拥有角色
     */
    boolean hasRole(Long userId, String roleCode);

    /**
     * 检查是否拥有任一角色（OR逻辑）
     *
     * @param userId    用户ID
     * @param roleCodes 角色编码列表
     * @return true-拥有任一角色
     */
    boolean hasAnyRole(Long userId, List<String> roleCodes);

    /**
     * 检查是否为超级管理员
     *
     * @param userId 用户ID
     * @return true-是超级管理员
     */
    boolean isSuperAdmin(Long userId);

    /**
     * 获取用户所有权限
     *
     * @param userId 用户ID
     * @return 权限标识列表
     */
    List<String> getUserPermissions(Long userId);

    /**
     * 获取用户所有角色
     *
     * @param userId 用户ID
     * @return 角色编码列表
     */
    List<String> getUserRoles(Long userId);
}
