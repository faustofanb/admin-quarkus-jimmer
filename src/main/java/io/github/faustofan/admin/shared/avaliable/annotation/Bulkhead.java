package io.github.faustofan.admin.shared.avaliable.annotation;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.*;

/**
 * 隔离舱注解
 * <p>
 * 在方法上使用此注解可自动应用隔离保护
 * <p>
 * 使用示例：
 * <pre>{@code
 * @Bulkhead(
 *     name = "heavyOperation",
 *     maxConcurrentCalls = 10,
 *     waitingTaskQueue = 20
 * )
 * public Data processHeavyOperation() {
 *     return heavyOperationService.process();
 * }
 * }</pre>
 */
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Documented
public @interface Bulkhead {

    /**
     * 隔离舱名称
     * <p>
     * 如果不指定，将使用 类名.方法名 作为默认名称
     */
    @Nonbinding
    String name() default "";

    /**
     * 最大并发调用数
     */
    @Nonbinding
    int maxConcurrentCalls() default 10;

    /**
     * 等待任务队列大小
     */
    @Nonbinding
    int waitingTaskQueue() default 10;

    /**
     * 等待超时（ISO-8601 duration 格式）
     * <p>
     * PT0S 表示不等待，直接拒绝
     */
    @Nonbinding
    String waitTimeout() default "PT0S";
}
