package io.github.faustofan.admin.shared.persistence;

import org.babyfish.jimmer.sql.MappedSuperclass;

/**
 * 租户审计基础实体（含租户字段）
 * <p>
 * 适用于含 tenant_id 的表（如 system_users/system_role/system_dept 等）。
 * 继承 AuditBaseEntity 的所有审计字段，并额外添加 tenantId。
 */
@MappedSuperclass
public interface TenantEntity extends AuditEntity {

    /**
     * 租户编号
     */
    long tenantId();
}
