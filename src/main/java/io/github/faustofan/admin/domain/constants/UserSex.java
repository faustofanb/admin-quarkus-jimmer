package io.github.faustofan.admin.domain.constants;

import org.babyfish.jimmer.sql.EnumItem;
import org.babyfish.jimmer.sql.EnumType;

/**
 * 用户性别枚举
 */
@EnumType(EnumType.Strategy.ORDINAL)
public enum UserSex {

    /**
     * 未知
     */
    @EnumItem(ordinal = 0)
    UNKNOWN,

    /**
     * 男
     */
    @EnumItem(ordinal = 1)
    MALE,

    /**
     * 女
     */
    @EnumItem(ordinal = 2)
    FEMALE
}