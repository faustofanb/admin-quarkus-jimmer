package io.github.faustofan.admin.domain.entity;

import io.github.faustofan.admin.domain.constants.CommonStatus;
import io.github.faustofan.admin.shared.persistence.AuditEntity;
import jakarta.annotation.Nullable;
import org.babyfish.jimmer.sql.Entity;
import org.babyfish.jimmer.sql.Table;

import java.time.Instant;

/**
 * 字典类型聚合根（system_dict_type）
 * <p>
 * 字典类型是数据字典的核心聚合根，负责：
 * <ul>
 * <li>字典分类管理</li>
 * <li>字典数据的生命周期管理</li>
 * </ul>
 */
@Entity
@Table(name = "system_dict_type")
public interface SystemDictType extends AuditEntity {

    /**
     * 字典名称
     */
    String name();

    /**
     * 字典类型
     */
    String type();

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
     * 删除时间（软删除时记录）
     */
    @Nullable
    Instant deletedTime();
}
