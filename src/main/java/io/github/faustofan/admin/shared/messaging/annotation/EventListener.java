package io.github.faustofan.admin.shared.messaging.annotation;

import io.github.faustofan.admin.shared.messaging.constants.ChannelType;
import io.github.faustofan.admin.shared.messaging.constants.EventType;
import jakarta.enterprise.util.Nonbinding;

import java.lang.annotation.*;

/**
 * 事件监听注解
 * <p>
 * 在方法上使用此注解可自动订阅并处理指定 Topic 的事件。
 * 框架会自动为标注的方法注册事件监听器。
 * <p>
 * 工作原理：
 * <ol>
 *   <li>扫描 @EventListener 注解的方法</li>
 *   <li>根据 topic 订阅对应的事件流</li>
 *   <li>事件到达时自动调用方法</li>
 *   <li>支持错误处理和重试</li>
 * </ol>
 * <p>
 * 使用示例：
 * <pre>{@code
 * @ApplicationScoped
 * public class UserEventHandler {
 *
 *     // 监听用户创建事件
 *     @EventListener(topic = "admin.system.user", eventType = EventType.CREATED)
 *     public void onUserCreated(DomainEvent<UserDto> event) {
 *         log.info("User created: " + event.getPayload().getUsername());
 *         // 处理用户创建后的逻辑
 *     }
 *
 *     // 监听所有用户事件
 *     @EventListener(topic = "admin.system.user")
 *     public void onUserEvent(DomainEvent<UserDto> event) {
 *         log.info("User event: " + event.getEventType());
 *     }
 *
 *     // 使用常量定义的 Topic
 *     @EventListener(
 *         topic = MessagingConstants.SystemTopic.USER_EVENTS,
 *         eventType = EventType.DELETED
 *     )
 *     public void onUserDeleted(DomainEvent<UserDto> event) {
 *         // 清理用户相关数据
 *     }
 *
 *     // 指定使用 Pulsar 通道
 *     @EventListener(
 *         topic = "admin.integration.order",
 *         channel = ChannelType.PULSAR,
 *         consumerGroup = "order-handler-group"
 *     )
 *     public void onOrderEvent(IntegrationEvent<OrderDto> event) {
 *         orderService.syncOrder(event.getPayload());
 *     }
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface EventListener {

    /**
     * 订阅的 Topic
     * <p>
     * 必填项，指定要监听的事件 Topic
     * <p>
     * 建议使用 {@code MessagingConstants.SystemTopic} 或 {@code MessagingConstants.BusinessTopic} 中定义的常量
     */
    @Nonbinding
    String topic();

    /**
     * 事件类型过滤
     * <p>
     * 可选，不指定则监听该 Topic 下所有事件类型
     */
    @Nonbinding
    EventType[] eventType() default {};

    /**
     * 消息通道类型
     * <p>
     * 默认使用配置的默认通道
     */
    @Nonbinding
    ChannelType channel() default ChannelType.AUTO;

    /**
     * 消费者组名称
     * <p>
     * 同一消费者组内的监听器会负载均衡，不同组会广播
     */
    @Nonbinding
    String consumerGroup() default "";

    /**
     * 是否异步处理
     * <p>
     * 如果为 true，事件将在独立线程中处理
     */
    @Nonbinding
    boolean async() default false;

    /**
     * 处理失败时的重试次数
     * <p>
     * 0 表示不重试
     */
    @Nonbinding
    int retryCount() default 0;

    /**
     * 重试间隔（ISO-8601 duration 格式）
     */
    @Nonbinding
    String retryInterval() default "PT1S";

    /**
     * 监听条件表达式（SpEL）
     * <p>
     * 返回 true 时才处理事件
     * <p>
     * 示例：{@code "#event.payload.status == 'ACTIVE'"}
     */
    @Nonbinding
    String condition() default "";

    /**
     * 优先级
     * <p>
     * 数值越小优先级越高，同一事件有多个监听器时按优先级顺序执行
     */
    @Nonbinding
    int priority() default 100;

    /**
     * 描述信息
     * <p>
     * 用于日志和监控
     */
    @Nonbinding
    String description() default "";
}
