package io.github.faustofan.admin.domain.entity;

import io.github.faustofan.admin.domain.constants.CommonStatus;
import io.github.faustofan.admin.shared.persistence.AuditEntity;
import jakarta.annotation.Nullable;
import org.babyfish.jimmer.sql.Entity;
import org.babyfish.jimmer.sql.Table;

/**
 * 租户套餐实体（system_tenant_package）
 * <p>
 * 注意：该表无 tenant_id，继承 AuditBaseEntity。
 * 含套餐名、状态、菜单ID列表等字段。
 */
@Entity
@Table(name = "system_tenant_package")
public interface SystemTenantPackage extends AuditEntity {

    /**
     * 套餐名
     */
    String name();

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
     * 关联的菜单编号（字符串存储，如 "[1,2,3,...]"）
     */
    String menuIds();
}
