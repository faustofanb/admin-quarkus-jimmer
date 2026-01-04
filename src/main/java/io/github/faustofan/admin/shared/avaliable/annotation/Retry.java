package io.github.faustofan.admin.shared.avaliable.annotation;

import io.github.faustofan.admin.shared.avaliable.constants.RetryStrategy;
import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.*;

/**
 * 重试注解
 * <p>
 * 在方法上使用此注解可自动应用重试机制
 * <p>
 * 使用示例：
 * <pre>{@code
 * @Retry(
 *     name = "orderService",
 *     maxRetries = 3,
 *     delay = "PT0.2S",
 *     strategy = RetryStrategy.EXPONENTIAL
 * )
 * public Order createOrder(OrderRequest request) {
 *     return orderService.create(request);
 * }
 * }</pre>
 */
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Documented
public @interface Retry {

    /**
     * 重试器名称
     * <p>
     * 如果不指定，将使用 类名.方法名 作为默认名称
     */
    @Nonbinding
    String name() default "";

    /**
     * 最大重试次数
     */
    @Nonbinding
    int maxRetries() default 3;

    /**
     * 重试延迟（ISO-8601 duration 格式）
     */
    @Nonbinding
    String delay() default "PT0.2S";

    /**
     * 重试策略
     */
    @Nonbinding
    RetryStrategy strategy() default RetryStrategy.EXPONENTIAL;

    /**
     * 抖动时间（ISO-8601 duration 格式）
     */
    @Nonbinding
    String jitter() default "PT0.05S";

    /**
     * 最大延迟时间（ISO-8601 duration 格式）
     */
    @Nonbinding
    String maxDelay() default "PT2S";

    /**
     * 应重试的异常类型
     */
    @Nonbinding
    Class<? extends Exception>[] retryOn() default {};

    /**
     * 不应重试的异常类型
     */
    @Nonbinding
    Class<? extends Exception>[] abortOn() default {};
}
