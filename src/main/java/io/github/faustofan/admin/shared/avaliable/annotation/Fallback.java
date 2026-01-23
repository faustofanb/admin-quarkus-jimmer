package io.github.faustofan.admin.shared.avaliable.annotation;

import io.github.faustofan.admin.shared.avaliable.constants.FallbackType;
import jakarta.enterprise.util.Nonbinding;

import java.lang.annotation.*;

/**
 * 回退注解
 * <p>
 * 在方法上使用此注解可自动应用回退处理
 * <p>
 * 使用示例：
 * <pre>{@code
 * @Fallback(
 *     name = "userService",
 *     fallbackMethod = "getUserFallback",
 *     type = FallbackType.CACHED
 * )
 * public User getUser(Long userId) {
 *     return externalUserService.getUser(userId);
 * }
 *
 * private User getUserFallback(Long userId) {
 *     return User.empty();
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@Documented
public @interface Fallback {

    /**
     * 回退器名称
     * <p>
     * 如果不指定，将使用 类名.方法名 作为默认名称
     */
    @Nonbinding
    String name() default "";

    /**
     * 回退方法名
     * <p>
     * 回退方法必须在同一个类中，且签名与原方法兼容
     */
    @Nonbinding
    String fallbackMethod() default "";

    /**
     * 回退类型
     */
    @Nonbinding
    FallbackType type() default FallbackType.DEFAULT_VALUE;
}
