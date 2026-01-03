package io.github.faustofan.admin.domain.constants;

import org.babyfish.jimmer.sql.EnumItem;
import org.babyfish.jimmer.sql.EnumType;

/**
 * 菜单类型枚举
 */
@EnumType(EnumType.Strategy.ORDINAL)
public enum MenuType {

    /**
     * 目录
     */
    @EnumItem(ordinal = 1)
    DIRECTORY,

    /**
     * 菜单
     */
    @EnumItem(ordinal = 2)
    MENU,

    /**
     * 按钮
     */
    @EnumItem(ordinal = 3)
    BUTTON
}
