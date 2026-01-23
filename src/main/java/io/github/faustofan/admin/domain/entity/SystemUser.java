package io.github.faustofan.admin.domain.entity;

import io.github.faustofan.admin.domain.constants.CommonStatus;
import io.github.faustofan.admin.domain.constants.UserSex;
import io.github.faustofan.admin.shared.persistence.TenantEntity;
import jakarta.annotation.Nullable;
import org.babyfish.jimmer.sql.*;

import java.time.Instant;
import java.util.List;

/**
 * 用户聚合根（system_users）
 * <p>
 * 用户是系统的核心聚合根，负责：
 * <ul>
 * <li>用户身份认证信息管理</li>
 * <li>用户基本信息维护</li>
 * <li>用户角色/岗位关联管理</li>
 * </ul>
 */
@Entity
@Table(name = "system_users")
public interface SystemUser extends TenantEntity {

    /**
     * 用户账号
     */
    String username();

    /**
     * 密码（加密存储）
     */
    String password();

    /**
     * 用户昵称
     */
    String nickname();

    /**
     * 备注
     */
    @Nullable
    String remark();

    /**
     * 部门ID
     */
    @Nullable
    Long deptId();

    /**
     * 岗位编号数组（字符串存储，如 "[1,2]"）
     */
    @Nullable
    String postIds();

    /**
     * 用户邮箱
     */
    @Nullable
    String email();

    /**
     * 手机号码
     */
    @Nullable
    String mobile();

    /**
     * 用户性别
     */
    @Nullable
    UserSex sex();

    /**
     * 头像地址
     */
    @Nullable
    String avatar();

    /**
     * 帐号状态
     */
    CommonStatus status();

    /**
     * 最后登录IP
     */
    @Nullable
    String loginIp();

    /**
     * 最后登录时间
     */
    @Nullable
    Instant loginDate();

    /**
     * 关联的用户-角色中间表
     */
    @ManyToMany
    @JoinTable(
        name = "system_user_role",
        joinColumnName = "user_id",
        inverseJoinColumnName = "role_id"
    )
    List<SystemRole> userRoles();

    /**
     * 关联的用户-岗位中间表
     */
    @ManyToMany
    @JoinTable(
        name = "system_user_post",
        joinColumnName = "user_id",
        inverseJoinColumnName = "post_id"
    )
    List<SystemPost> userPosts();
}

