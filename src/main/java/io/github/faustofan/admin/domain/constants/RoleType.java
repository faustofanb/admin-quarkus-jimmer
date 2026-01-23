package io.github.faustofan.admin.domain.constants;

import org.babyfish.jimmer.sql.EnumItem;
import org.babyfish.jimmer.sql.EnumType;

/**
 * 角色类型枚举
 */
@EnumType(EnumType.Strategy.ORDINAL)
public enum RoleType {

    /**
     * 内置角色
     */
    @EnumItem(ordinal = 1)
    SYSTEM,

    /**
     * 自定义角色
     */
    @EnumItem(ordinal = 2)
    CUSTOM
}
