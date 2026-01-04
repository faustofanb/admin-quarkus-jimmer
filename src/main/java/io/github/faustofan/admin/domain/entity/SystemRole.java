package io.github.faustofan.admin.domain.entity;

import io.github.faustofan.admin.domain.constants.CommonStatus;
import io.github.faustofan.admin.domain.constants.DataScope;
import io.github.faustofan.admin.domain.constants.RoleType;
import io.github.faustofan.admin.shared.persistence.TenantEntity;
import jakarta.annotation.Nullable;
import org.babyfish.jimmer.sql.*;

import java.util.List;

/**
 * 角色聚合根（system_role）
 * <p>
 * 角色是权限管理的核心聚合根，负责：
 * <ul>
 * <li>角色基本信息管理</li>
 * <li>数据权限范围控制</li>
 * <li>菜单权限关联</li>
 * </ul>
 */
@Entity
@Table(name = "system_role")
public interface SystemRole extends TenantEntity {

    /**
     * 角色名称
     */
    String name();

    /**
     * 角色权限字符串（如 super_admin）
     */
    String code();

    /**
     * 显示顺序
     */
    int sort();

    /**
     * 数据范围
     */
    DataScope dataScope();

    /**
     * 数据范围（指定部门数组，字符串存储如 "[100,102,103]"）
     */
    String dataScopeDeptIds();

    /**
     * 角色状态
     */
    CommonStatus status();

    /**
     * 角色类型
     */
    RoleType type();

    /**
     * 备注
     */
    @Nullable
    String remark();

    /**
     * 关联的角色-菜单中间表
     */
    @ManyToMany
    @JoinTable(
            name = "system_role_menu",
            joinColumnName = "role_id",
            inverseJoinColumnName = "menu_id"
    )
    List<SystemMenu> roleMenus();

    /**
     * 关联的用户-角色中间表（镜像关联）
     * <p>
     * 注意：这是 SystemUser.userRoles() 的镜像，主控方在 SystemUser 端
     */
    @ManyToMany(mappedBy = "userRoles")
    List<SystemUser> roleUsers();
}
