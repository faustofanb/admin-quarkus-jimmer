package io.github.faustofan.admin.domain.entity;


import io.github.faustofan.admin.domain.constants.CommonStatus;
import io.github.faustofan.admin.shared.persistence.TenantEntity;
import jakarta.annotation.Nullable;
import org.babyfish.jimmer.sql.*;

import java.util.List;

/**
 * 部门聚合根（system_dept）
 * <p>
 * 部门是组织架构的核心聚合根，负责：
 * <ul>
 * <li>部门树形结构管理</li>
 * <li>负责人管理</li>
 * <li>数据权限边界定义</li>
 * </ul>
 */
@Entity
@Table(name = "system_dept")
public interface SystemDept extends TenantEntity {
    /**
     * 部门名称
     */
    String name();

    /**
     * 显示顺序
     */
    int sort();

    /**
     * 负责人的用户ID
     */
    @Nullable
    Long leaderUserId();

    /**
     * 联系电话
     */
    @Nullable
    String phone();

    /**
     * 邮箱
     */
    @Nullable
    String email();

    /**
     * 部门状态
     */
    CommonStatus status();

    /**
     * 父部门（自关联）
     */
    @ManyToOne
    @JoinColumn(name = "parent_id")
    @OnDissociate(DissociateAction.SET_NULL)
    @Nullable
    SystemDept parent();

    /**
     * 子部门列表（自关联）
     */
    @OneToMany(mappedBy = "parent")
    List<SystemDept> children();
}
