package io.github.faustofan.admin.shared.avaliable.annotation;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.*;

/**
 * 组合保护注解
 * <p>
 * 在方法上使用此注解可应用多重保护机制
 * <p>
 * 此注解是 @RateLimit + @CircuitBreaker + @Retry + @Timeout 的组合
 * <p>
 * 使用示例：
 * <pre>{@code
 * @Protect(
 *     name = "criticalService",
 *     mode = ProtectMode.FULL,
 *     fallbackMethod = "criticalServiceFallback"
 * )
 * public Response execute(Request request) {
 *     return criticalService.execute(request);
 * }
 *
 * private Response criticalServiceFallback(Request request) {
 *     return Response.degraded();
 * }
 * }</pre>
 */
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Documented
public @interface Protect {

    /**
     * 保护器名称
     * <p>
     * 如果不指定，将使用 类名.方法名 作为默认名称
     */
    @Nonbinding
    String name() default "";

    /**
     * 保护模式
     */
    @Nonbinding
    ProtectMode mode() default ProtectMode.FULL;

    /**
     * 回退方法名
     * <p>
     * 回退方法必须在同一个类中，且签名与原方法兼容
     */
    @Nonbinding
    String fallbackMethod() default "";

    /**
     * 保护模式枚举
     */
    enum ProtectMode {
        /**
         * 全保护模式：限流 + 熔断 + 降级 + 超时 + 重试 + 回退
         */
        FULL,

        /**
         * 标准保护模式：熔断 + 重试 + 回退
         */
        STANDARD,

        /**
         * 轻量保护模式：熔断 + 回退
         */
        LIGHT
    }
}
