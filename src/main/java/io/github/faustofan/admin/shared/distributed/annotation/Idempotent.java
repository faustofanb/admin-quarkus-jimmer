package io.github.faustofan.admin.shared.distributed.annotation;

import io.github.faustofan.admin.shared.distributed.idempotent.IdempotentStrategy;
import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.*;

/**
 * 幂等注解
 * <p>
 * 在方法上使用此注解可自动实现幂等检查，防止重复请求。
 * <p>
 * 工作原理：
 * <ol>
 *   <li>解析幂等Key（支持 SpEL 表达式）</li>
 *   <li>检查是否为首次请求</li>
 *   <li>首次请求：执行方法并标记</li>
 *   <li>重复请求：抛出异常或返回默认值</li>
 * </ol>
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 基于请求参数的幂等
 * @Idempotent(key = "'order:create:' + #request.orderId")
 * public Order createOrder(OrderRequest request) {
 *     return orderService.create(request);
 * }
 *
 * // 基于 Token 的幂等
 * @Idempotent(
 *     key = "#token",
 *     strategy = IdempotentStrategy.TOKEN,
 *     ttl = "PT30M"
 * )
 * public void submitPayment(String token, PaymentRequest request) {
 *     paymentService.submit(request);
 * }
 *
 * // 自定义重复请求处理
 * @Idempotent(
 *     key = "'user:update:' + #userId",
 *     message = "请勿重复提交用户更新请求",
 *     throwOnDuplicate = true
 * )
 * public User updateUser(Long userId, UserRequest request) {
 *     return userService.update(userId, request);
 * }
 * }</pre>
 */
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Documented
public @interface Idempotent {

    /**
     * 幂等Key表达式
     * <p>
     * 支持 SpEL 表达式，可使用方法参数：
     * <ul>
     *   <li>{@code #paramName} - 参数名</li>
     *   <li>{@code #p0, #p1} - 参数索引</li>
     *   <li>{@code #request.xxx} - 参数属性</li>
     * </ul>
     * <p>
     * 如不指定，将使用 "类名:方法名:参数哈希" 作为默认Key
     */
    @Nonbinding
    String key() default "";

    /**
     * 幂等策略
     */
    @Nonbinding
    IdempotentStrategy strategy() default IdempotentStrategy.PARAM;

    /**
     * 幂等Key过期时间（ISO-8601 duration 格式）
     * <p>
     * 示例：
     * <ul>
     *   <li>{@code PT5M} - 5分钟</li>
     *   <li>{@code PT1H} - 1小时</li>
     * </ul>
     * <p>
     * 不指定则使用默认配置
     */
    @Nonbinding
    String ttl() default "";

    /**
     * 重复请求时是否抛出异常
     * <p>
     * 如果为 true，重复请求时抛出 {@code DistributedException}；
     * 如果为 false，返回 null（对于有返回值的方法）
     */
    @Nonbinding
    boolean throwOnDuplicate() default true;

    /**
     * 重复请求时的错误消息
     */
    @Nonbinding
    String message() default "重复请求，请勿重复提交";

    /**
     * 执行失败时是否移除幂等标记
     * <p>
     * 如果为 true，方法执行异常时会移除幂等标记，允许重试；
     * 如果为 false，即使失败也保留标记
     */
    @Nonbinding
    boolean removeOnFailure() default true;

    /**
     * 幂等检查条件表达式（SpEL）
     * <p>
     * 返回 true 时才执行幂等检查
     * <p>
     * 示例：{@code "#request.amount > 0"}
     */
    @Nonbinding
    String condition() default "";

    /**
     * Key前缀
     * <p>
     * 用于区分不同业务的幂等Key
     */
    @Nonbinding
    String prefix() default "";
}
