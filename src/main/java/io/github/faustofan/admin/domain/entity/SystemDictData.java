package io.github.faustofan.admin.domain.entity;

import io.github.faustofan.admin.domain.constants.CommonStatus;
import io.github.faustofan.admin.shared.persistence.AuditEntity;
import jakarta.annotation.Nullable;
import org.babyfish.jimmer.sql.Entity;
import org.babyfish.jimmer.sql.Table;

/**
 * 字典数据实体（system_dict_data）
 * <p>
 * 注意：字典数据是字典类型聚合根下的实体，不是独立的聚合根。
 * 通过 dictType 字段关联到对应的字典类型。
 */
@Entity
@Table(name = "system_dict_data")
public interface SystemDictData extends AuditEntity {

    /**
     * 字典排序
     */
    int sort();

    /**
     * 字典标签
     */
    String label();

    /**
     * 字典键值
     */
    String value();

    /**
     * 字典类型编码
     * <p>
     * 存储 system_dict_type.type 的值（字符串）
     * 注意：Jimmer 不支持非主键关联，因此这里不使用 @ManyToOne，而是直接存储 type 值
     */
    String dictType();

    /**
     * 状态
     */
    CommonStatus status();

    /**
     * 颜色类型
     */
    @Nullable
    String colorType();

    /**
     * CSS 样式
     */
    @Nullable
    String cssClass();

    /**
     * 备注
     */
    @Nullable
    String remark();

}
