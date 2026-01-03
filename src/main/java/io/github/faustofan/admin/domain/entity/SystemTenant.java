package io.github.faustofan.admin.domain.entity;

import io.github.faustofan.admin.domain.constants.CommonStatus;
import io.github.faustofan.admin.shared.persistence.AuditEntity;
import jakarta.annotation.Nullable;
import org.babyfish.jimmer.sql.Entity;
import org.babyfish.jimmer.sql.Table;

import java.time.Instant;

/**
 * 租户实体（system_tenant）
 * <p>
 * 注意：该表自身无 tenant_id（因为它就是租户表），继承 AuditEntity。
 * 含租户名、联系人、状态、套餐、过期时间等字段。
 */
@Entity
@Table(name = "system_tenant")
public interface SystemTenant extends AuditEntity {

    /**
     * 租户名
     */
    String name();

    /**
     * 联系人的用户编号
     */
    @Nullable
    Long contactUserId();

    /**
     * 联系人
     */
    String contactName();

    /**
     * 联系手机
     */
    @Nullable
    String contactMobile();

    /**
     * 租户状态
     */
    CommonStatus status();

    /**
     * 绑定域名数组（字符串存储）
     */
    @Nullable
    String websites();

    /**
     * 租户套餐编号
     */
    long packageId();

    /**
     * 过期时间
     */
    Instant expireTime();

    /**
     * 账号数量
     */
    int accountCount();
}
