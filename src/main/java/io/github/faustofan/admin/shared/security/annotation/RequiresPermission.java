package io.github.faustofan.admin.shared.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 需要权限注解
 * <p>
 * 标注在方法或类上，表示需要用户拥有指定权限才能访问。
 * 支持多个权限，可通过logical属性指定逻辑关系。
 *
 * <h3>使用示例：</h3>
 * <pre>
 * &#64;RequiresPermission("system:user:query")
 * public void queryUser() { ... }
 *
 * &#64;RequiresPermission(value = {"system:user:create", "system:user:update"}, logical = Logical.OR)
 * public void saveUser() { ... }
 * </pre>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    /**
     * 权限标识，支持多个
     */
    String[] value();

    /**
     * 逻辑关系，默认AND
     */
    Logical logical() default Logical.AND;

    /**
     * 逻辑枚举
     */
    enum Logical {
        /** 需要拥有所有权限 */
        AND,
        /** 需要拥有任一权限 */
        OR
    }
}
