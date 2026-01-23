package io.github.faustofan.admin.shared.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 允许匿名访问注解
 * <p>
 * 标注在方法或类上，表示允许匿名（未登录）用户访问。
 * 优先级高于 {@link RequiresAuthentication}。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Anonymous {
}
