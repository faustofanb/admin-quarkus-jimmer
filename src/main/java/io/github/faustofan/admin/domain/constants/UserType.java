package io.github.faustofan.admin.domain.constants;

import org.babyfish.jimmer.sql.EnumItem;
import org.babyfish.jimmer.sql.EnumType;

/**
 * 用户类型枚举
 */
@EnumType(EnumType.Strategy.ORDINAL)
public enum UserType {

    /**
     * 后台管理员
     */
    @EnumItem(ordinal = 1)
    ADMIN,

    /**
     * 前台会员
     */
    @EnumItem(ordinal = 2)
    MEMBER
}

