package io.github.faustofan.admin.shared.avaliable.annotation;

import io.github.faustofan.admin.shared.avaliable.constants.RateLimitAlgorithm;
import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.*;

/**
 * 限流注解
 * <p>
 * 在方法上使用此注解可自动应用限流保护
 * <p>
 * 使用示例：
 * <pre>{@code
 * @RateLimit(name = "api:user:query", permits = 100, window = "PT1S")
 * public List<User> queryUsers(QueryRequest request) {
 *     return userRepository.findAll();
 * }
 * }</pre>
 */
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Documented
public @interface RateLimit {

    /**
     * 限流器名称
     * <p>
     * 如果不指定，将使用 类名.方法名 作为默认名称
     */
    @Nonbinding
    String name() default "";

    /**
     * 每时间窗口允许的请求数
     */
    @Nonbinding
    int permits() default 100;

    /**
     * 时间窗口（ISO-8601 duration 格式，如 PT1S 表示 1 秒）
     */
    @Nonbinding
    String window() default "PT1S";

    /**
     * 限流算法
     */
    @Nonbinding
    RateLimitAlgorithm algorithm() default RateLimitAlgorithm.SLIDING_WINDOW;

    /**
     * 是否使用分布式限流
     */
    @Nonbinding
    boolean distributed() default false;

    /**
     * 当限流时是否抛出异常
     * <p>
     * 如果为 true，超过限流时抛出 RateLimitExceededException
     * 如果为 false，超过限流时返回 fallback 值（如果配置了）或 null
     */
    @Nonbinding
    boolean throwException() default true;
}
