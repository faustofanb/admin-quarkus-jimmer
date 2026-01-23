package io.github.faustofan.admin.domain.constants;

import org.babyfish.jimmer.sql.EnumItem;
import org.babyfish.jimmer.sql.EnumType;

/**
 * 通用状态枚举
 * <p>
 * 适用于用户、角色、部门、岗位、字典等实体的状态字段（0=正常，1=停用）
 */
@EnumType(EnumType.Strategy.ORDINAL)
public enum CommonStatus {

    /**
     * 正常
     */
    @EnumItem(ordinal = 0)
    NORMAL,

    /**
     * 停用
     */
    @EnumItem(ordinal = 1)
    DISABLED
}