package io.github.faustofan.admin.shared.distributed.annotation;

import io.github.faustofan.admin.shared.distributed.constants.LockType;
import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.*;

/**
 * 分布式锁注解
 * <p>
 * 在方法上使用此注解可自动在执行前获取锁，执行后释放锁。
 * <p>
 * 工作原理：
 * <ol>
 *   <li>解析锁Key（支持 SpEL 表达式）</li>
 *   <li>尝试获取锁（可配置等待时间）</li>
 *   <li>获取成功：执行方法</li>
 *   <li>执行完成：自动释放锁</li>
 *   <li>获取失败：抛出异常或返回默认值</li>
 * </ol>
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 基础用法：保护订单创建
 * @DistributedLock(key = "'order:create:' + #userId")
 * public Order createOrder(Long userId, OrderRequest request) {
 *     return orderService.create(request);
 * }
 *
 * // 自定义等待时间和租约时间
 * @DistributedLock(
 *     key = "'user:update:' + #userId",
 *     waitTime = "PT5S",
 *     leaseTime = "PT30S"
 * )
 * public User updateUser(Long userId, UserRequest request) {
 *     return userService.update(userId, request);
 * }
 *
 * // 使用本地锁（单实例场景）
 * @DistributedLock(
 *     key = "'report:generate:' + #reportId",
 *     type = LockType.LOCAL
 * )
 * public Report generateReport(Long reportId) {
 *     return reportService.generate(reportId);
 * }
 *
 * // 获取锁失败时不抛异常
 * @DistributedLock(
 *     key = "'task:' + #taskId",
 *     throwOnFailure = false
 * )
 * public void processTask(Long taskId) {
 *     // 获取锁失败时直接返回，不执行
 * }
 * }</pre>
 */
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Documented
public @interface DistributedLock {

    /**
     * 锁Key表达式
     * <p>
     * 支持 SpEL 表达式，可使用方法参数：
     * <ul>
     *   <li>{@code #paramName} - 参数名</li>
     *   <li>{@code #p0, #p1} - 参数索引</li>
     *   <li>{@code #request.xxx} - 参数属性</li>
     * </ul>
     * <p>
     * 如不指定，将使用 "类名:方法名" 作为默认Key
     */
    @Nonbinding
    String key() default "";

    /**
     * 锁类型
     * <p>
     * 默认使用配置的锁类型
     */
    @Nonbinding
    LockType type() default LockType.AUTO;

    /**
     * 等待获取锁的最大时间（ISO-8601 duration 格式）
     * <p>
     * 示例：
     * <ul>
     *   <li>{@code PT5S} - 5秒</li>
     *   <li>{@code PT10S} - 10秒</li>
     * </ul>
     * <p>
     * 不指定则使用默认配置
     */
    @Nonbinding
    String waitTime() default "";

    /**
     * 锁的租约时间（ISO-8601 duration 格式）
     * <p>
     * 锁的自动过期时间，防止死锁
     * <p>
     * 不指定则使用默认配置
     */
    @Nonbinding
    String leaseTime() default "";

    /**
     * 获取锁失败时是否抛出异常
     * <p>
     * 如果为 true，获取锁失败时抛出 {@code DistributedException}；
     * 如果为 false，直接返回 null（对于有返回值的方法）或不执行
     */
    @Nonbinding
    boolean throwOnFailure() default true;

    /**
     * 获取锁失败时的错误消息
     */
    @Nonbinding
    String message() default "获取锁失败，请稍后重试";

    /**
     * 锁Key前缀
     * <p>
     * 用于区分不同业务的锁
     */
    @Nonbinding
    String prefix() default "";

    /**
     * 锁条件表达式（SpEL）
     * <p>
     * 返回 true 时才尝试获取锁
     * <p>
     * 示例：{@code "#amount > 100"}
     */
    @Nonbinding
    String condition() default "";
}
