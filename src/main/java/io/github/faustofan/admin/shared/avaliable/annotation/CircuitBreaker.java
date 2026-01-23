package io.github.faustofan.admin.shared.avaliable.annotation;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.*;

/**
 * 熔断器注解
 * <p>
 * 在方法上使用此注解可自动应用熔断保护
 * <p>
 * 使用示例：
 * <pre>{@code
 * @CircuitBreaker(
 *     name = "userService",
 *     failureRatio = 0.5,
 *     requestVolumeThreshold = 20,
 *     delay = "PT5S"
 * )
 * public User getUser(Long userId) {
 *     return externalUserService.getUser(userId);
 * }
 * }</pre>
 */
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Documented
public @interface CircuitBreaker {

    /**
     * 熔断器名称
     * <p>
     * 如果不指定，将使用 类名.方法名 作为默认名称
     */
    @Nonbinding
    String name() default "";

    /**
     * 失败率阈值（0.0-1.0）
     * <p>
     * 当失败率超过此值时，熔断器打开
     */
    @Nonbinding
    double failureRatio() default 0.5;

    /**
     * 请求量阈值
     * <p>
     * 用于计算失败率的最小请求数
     */
    @Nonbinding
    int requestVolumeThreshold() default 20;

    /**
     * 熔断延迟时间（ISO-8601 duration 格式）
     * <p>
     * 从 OPEN 状态到 HALF_OPEN 状态的等待时间
     */
    @Nonbinding
    String delay() default "PT5S";

    /**
     * 成功阈值
     * <p>
     * 半开状态需要连续成功的请求数才能关闭熔断器
     */
    @Nonbinding
    int successThreshold() default 5;

    /**
     * 滚动窗口大小
     */
    @Nonbinding
    int rollingWindow() default 10;
}
