package io.github.faustofan.admin.shared.messaging.annotation;

import io.github.faustofan.admin.shared.messaging.constants.ChannelType;
import io.github.faustofan.admin.shared.messaging.constants.DeliveryMode;
import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.*;

/**
 * 事件发布注解
 * <p>
 * 在方法上使用此注解可在方法执行后自动发布事件。
 * <p>
 * 工作原理：
 * <ol>
 *   <li>方法执行完成</li>
 *   <li>从返回值或参数构建事件</li>
 *   <li>发布到指定 Topic</li>
 * </ol>
 * <p>
 * 使用示例：
 * <pre>{@code
 * @ApplicationScoped
 * public class UserService {
 *
 *     // 方法执行后发布事件（返回值作为 payload）
 *     @EventPublish(
 *         topic = "admin.system.user",
 *         eventType = "created"
 *     )
 *     public User createUser(UserRequest request) {
 *         return userRepository.save(convertToUser(request));
 *     }
 *
 *     // 使用 SpEL 表达式构建 payload
 *     @EventPublish(
 *         topic = "admin.system.user",
 *         eventType = "updated",
 *         payload = "#result"
 *     )
 *     public User updateUser(Long userId, UserRequest request) {
 *         return userRepository.update(userId, request);
 *     }
 *
 *     // 条件发布
 *     @EventPublish(
 *         topic = "admin.system.user",
 *         eventType = "deleted",
 *         condition = "#result == true"
 *     )
 *     public boolean deleteUser(Long userId) {
 *         return userRepository.deleteById(userId);
 *     }
 *
 *     // 异步发布
 *     @EventPublish(
 *         topic = "admin.business.order",
 *         eventType = "created",
 *         async = true,
 *         deliveryMode = DeliveryMode.AT_LEAST_ONCE
 *     )
 *     public Order createOrder(OrderRequest request) {
 *         return orderRepository.save(request);
 *     }
 * }
 * }</pre>
 */
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Documented
public @interface EventPublish {

    /**
     * 发布的 Topic
     * <p>
     * 必填项，指定事件发布到哪个 Topic
     */
    @Nonbinding
    String topic();

    /**
     * 事件类型
     * <p>
     * 用于标识事件类型，如 "created", "updated", "deleted"
     */
    @Nonbinding
    String eventType() default "custom";

    /**
     * 消息通道类型
     */
    @Nonbinding
    ChannelType channel() default ChannelType.AUTO;

    /**
     * 投递模式
     */
    @Nonbinding
    DeliveryMode deliveryMode() default DeliveryMode.FIRE_AND_FORGET;

    /**
     * Payload 表达式（SpEL）
     * <p>
     * 不指定则使用方法返回值
     * <p>
     * 支持：
     * <ul>
     *   <li>{@code #result} - 方法返回值</li>
     *   <li>{@code #paramName} - 方法参数</li>
     * </ul>
     */
    @Nonbinding
    String payload() default "";

    /**
     * 事件来源
     * <p>
     * 不指定则使用 类名.方法名
     */
    @Nonbinding
    String source() default "";

    /**
     * 是否异步发布
     */
    @Nonbinding
    boolean async() default false;

    /**
     * 发布条件表达式（SpEL）
     * <p>
     * 返回 true 时才发布事件
     */
    @Nonbinding
    String condition() default "";

    /**
     * 是否在方法执行前发布（默认执行后）
     */
    @Nonbinding
    boolean beforeInvocation() default false;

    /**
     * 发布失败时是否抛出异常
     * <p>
     * 如果为 false，发布失败只记录日志不影响业务
     */
    @Nonbinding
    boolean throwOnFailure() default false;
}
