package io.github.faustofan.admin.shared.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 需要角色注解
 * <p>
 * 标注在方法或类上，表示需要用户拥有指定角色才能访问。
 *
 * <h3>使用示例：</h3>
 * <pre>
 * &#64;RequiresRole("super_admin")
 * public void adminOnly() { ... }
 *
 * &#64;RequiresRole(value = {"admin", "manager"}, logical = Logical.OR)
 * public void managerOrAdmin() { ... }
 * </pre>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresRole {

    /**
     * 角色编码，支持多个
     */
    String[] value();

    /**
     * 逻辑关系，默认AND
     */
    RequiresPermission.Logical logical() default RequiresPermission.Logical.AND;
}
