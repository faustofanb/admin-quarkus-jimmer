package io.github.faustofan.admin.domain.entity;

import io.github.faustofan.admin.domain.constants.CommonStatus;
import io.github.faustofan.admin.domain.constants.MenuType;
import io.github.faustofan.admin.shared.persistence.AuditEntity;
import jakarta.annotation.Nullable;
import org.babyfish.jimmer.sql.*;

import java.util.List;

/**
 * 菜单聚合根（system_menu）
 * <p>
 * 菜单是权限系统的核心聚合根，负责：
 * <ul>
 * <li>菜单树形结构管理</li>
 * <li>权限标识管理</li>
 * <li>前端路由配置</li>
 * </ul>
 */
@Entity
@Table(name = "system_menu")
public interface SystemMenu extends AuditEntity {

    /**
     * 菜单名称
     */
    String name();

    /**
     * 权限标识（如 system:user:list）
     */
    String permission();

    /**
     * 菜单类型
     */
    MenuType type();

    /**
     * 显示顺序
     */
    int sort();

    /**
     * 路由地址
     */
    @Nullable
    String path();

    /**
     * 菜单图标
     */
    @Nullable
    String icon();

    /**
     * 组件路径
     */
    @Nullable
    String component();

    /**
     * 组件名
     */
    @Nullable
    String componentName();

    /**
     * 菜单状态
     */
    CommonStatus status();

    /**
     * 是否可见
     */
    boolean visible();

    /**
     * 是否缓存
     */
    boolean keepAlive();

    /**
     * 是否总是显示
     */
    boolean alwaysShow();

    /**
     * 父菜单（自关联）
     */
    @ManyToOne
    @JoinColumn(name = "parent_id")
    @OnDissociate(DissociateAction.SET_NULL)
    @Nullable
    SystemMenu parent();

    /**
     * 子菜单列表（自关联）
     */
    @OneToMany(mappedBy = "parent")
    List<SystemMenu> children();

    /**
     * 关联的角色（通过 system_role_menu 中间表）
     */
//    @OneToMany(mappedBy = "menu")
//    List<SystemRoleMenu> roleMenus();

}
