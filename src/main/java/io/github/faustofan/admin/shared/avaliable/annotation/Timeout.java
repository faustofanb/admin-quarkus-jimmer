package io.github.faustofan.admin.shared.avaliable.annotation;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.*;

/**
 * 超时注解
 * <p>
 * 在方法上使用此注解可自动应用超时控制
 * <p>
 * 使用示例：
 * <pre>{@code
 * @Timeout(name = "externalApi", duration = "PT3S")
 * public Result callExternalApi() {
 *     return externalApiClient.call();
 * }
 * }</pre>
 */
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Documented
public @interface Timeout {

    /**
     * 超时器名称
     * <p>
     * 如果不指定，将使用 类名.方法名 作为默认名称
     */
    @Nonbinding
    String name() default "";

    /**
     * 超时时间（ISO-8601 duration 格式）
     */
    @Nonbinding
    String duration() default "PT5S";

    /**
     * 是否启用超时指标收集
     */
    @Nonbinding
    boolean metricsEnabled() default true;
}
