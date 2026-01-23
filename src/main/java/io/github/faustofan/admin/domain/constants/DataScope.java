package io.github.faustofan.admin.domain.constants;

import org.babyfish.jimmer.sql.EnumItem;
import org.babyfish.jimmer.sql.EnumType;

/**
 * 数据范围（数据权限）枚举
 */
@EnumType(EnumType.Strategy.ORDINAL)
public enum DataScope {

    /**
     * 全部数据权限
     */
    @EnumItem(ordinal = 1)
    ALL,

    /**
     * 自定义数据权限
     */
    @EnumItem(ordinal = 2)
    CUSTOM,

    /**
     * 本部门数据权限
     */
    @EnumItem(ordinal = 3)
    DEPT,

    /**
     * 本部门及以下数据权限
     */
    @EnumItem(ordinal = 4)
    DEPT_AND_CHILD
}