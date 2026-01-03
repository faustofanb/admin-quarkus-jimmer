package io.github.faustofan.admin.domain.entity;

import io.github.faustofan.admin.domain.constants.CommonStatus;
import io.github.faustofan.admin.shared.persistence.TenantEntity;
import jakarta.annotation.Nullable;
import org.babyfish.jimmer.sql.*;

import java.util.List;

/**
 * 岗位聚合根（system_post）
 * <p>
 * 岗位是人力资源管理的核心聚合根，负责：
 * <ul>
 * <li>岗位信息管理</li>
 * <li>用户岗位分配</li>
 * </ul>
 */
@Entity
@Table(name = "system_post")
public interface SystemPost extends TenantEntity {
    /**
     * 岗位编码（如 ceo, se, user）
     */
    String code();

    /**
     * 岗位名称
     */
    String name();

    /**
     * 显示顺序
     */
    int sort();

    /**
     * 状态
     */
    CommonStatus status();

    /**
     * 备注
     */
    @Nullable
    String remark();

    /**
     * 关联的用户-岗位中间表
     */
    @ManyToMany
    @JoinTable(
            name = "system_user_post",
            joinColumnName = "post_id",
            inverseJoinColumnName = "user_id"
    )
    List<SystemUser> postUsers();

}
