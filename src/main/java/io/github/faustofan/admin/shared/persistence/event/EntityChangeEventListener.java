package io.github.faustofan.admin.shared.persistence.event;

import io.github.faustofan.admin.shared.messaging.annotation.EventListener;
import io.github.faustofan.admin.shared.messaging.constants.EventType;
import io.github.faustofan.admin.shared.messaging.constants.MessagingConstants;
import io.github.faustofan.admin.shared.messaging.core.DomainEvent;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * 实体变更事件监听器示例
 * <p>
 * 演示如何使用 {@code @EventListener} 注解监听实体变更事件。
 * <p>
 * <b>使用说明：</b>
 * <ul>
 *     <li>所有继承 {@code AuditEntity} 的实体在执行 INSERT/UPDATE/DELETE 时会自动发布事件</li>
 *     <li>事件 Topic 使用 {@link MessagingConstants.EntityTopic} 中定义的常量</li>
 *     <li>可以通过 {@code eventType} 参数过滤特定的事件类型</li>
 * </ul>
 *
 * <b>Topic 常量示例：</b>
 * <ul>
 *     <li>{@link MessagingConstants.EntityTopic#SYSTEM_USER} → admin.domain.systemuser</li>
 *     <li>{@link MessagingConstants.EntityTopic#SYSTEM_ROLE} → admin.domain.systemrole</li>
 *     <li>{@link MessagingConstants.EntityTopic#SYSTEM_MENU} → admin.domain.systemmenu</li>
 * </ul>
 */
@ApplicationScoped
public class EntityChangeEventListener {

    private static final Logger LOG = Logger.getLogger(EntityChangeEventListener.class);

    // ===========================
    // 用户实体事件
    // ===========================

    /**
     * 监听用户创建事件
     */
    @EventListener(
            topic = MessagingConstants.EntityTopic.SYSTEM_USER,
            eventType = EventType.CREATED,
            description = "用户创建事件处理"
    )
    public void onUserCreated(DomainEvent<EntityChangeEvent<?>> event) {
        EntityChangeEvent<?> changeEvent = event.getPayload();
        LOG.infov("User created: id={0}, source={1}",
                changeEvent.getEntityId(), changeEvent.getSource());

        // 示例：发送欢迎邮件、初始化用户配置等
    }

    /**
     * 监听用户更新事件
     */
    @EventListener(
            topic = MessagingConstants.EntityTopic.SYSTEM_USER,
            eventType = EventType.UPDATED,
            description = "用户更新事件处理"
    )
    public void onUserUpdated(DomainEvent<EntityChangeEvent<?>> event) {
        EntityChangeEvent<?> changeEvent = event.getPayload();
        LOG.debugv("User updated: id={0}, oldEntity={1}, newEntity={2}",
                changeEvent.getEntityId(),
                changeEvent.getOldEntity() != null,
                changeEvent.getNewEntity() != null);

        // 示例：同步用户信息到其他系统、更新缓存等
    }

    /**
     * 监听用户删除事件
     */
    @EventListener(
            topic = MessagingConstants.EntityTopic.SYSTEM_USER,
            eventType = EventType.DELETED,
            description = "用户删除事件处理"
    )
    public void onUserDeleted(DomainEvent<EntityChangeEvent<?>> event) {
        EntityChangeEvent<?> changeEvent = event.getPayload();
        LOG.infov("User deleted: id={0}", changeEvent.getEntityId());

        // 示例：清理用户相关数据、通知相关方等
    }

    // ===========================
    // 角色实体事件
    // ===========================

    /**
     * 监听所有角色变更事件
     */
    @EventListener(
            topic = MessagingConstants.EntityTopic.SYSTEM_ROLE,
            description = "角色变更事件处理"
    )
    public void onRoleChanged(DomainEvent<EntityChangeEvent<?>> event) {
        EntityChangeEvent<?> changeEvent = event.getPayload();
        LOG.debugv("Role {0}: id={1}",
                changeEvent.getEventType().getCode(),
                changeEvent.getEntityId());

        // 示例：刷新权限缓存、通知在线用户权限变更等
    }

    // ===========================
    // 菜单实体事件
    // ===========================

    /**
     * 监听菜单变更事件
     */
    @EventListener(
            topic = MessagingConstants.EntityTopic.SYSTEM_MENU,
            description = "菜单变更事件处理"
    )
    public void onMenuChanged(DomainEvent<EntityChangeEvent<?>> event) {
        EntityChangeEvent<?> changeEvent = event.getPayload();
        LOG.debugv("Menu {0}: id={1}",
                changeEvent.getEventType().getCode(),
                changeEvent.getEntityId());

        // 示例：刷新菜单缓存、更新前端路由等
    }

    // ===========================
    // 通用事件处理（调试用）
    // ===========================

    /**
     * 监听所有领域事件（调试用途）
     * <p>
     * 注意：生产环境中应该禁用或设置适当的日志级别
     */
    // @EventListener(
    //         topic = MessagingConstants.TopicPrefix.DOMAIN + "*",
    //         async = true,
    //         priority = 1000,
    //         description = "通用领域事件日志"
    // )
    // public void onAnyDomainEvent(DomainEvent<EntityChangeEvent<?>> event) {
    //     EntityChangeEvent<?> changeEvent = event.getPayload();
    //     LOG.debugv("[DOMAIN EVENT] type={0}, entity={1}, id={2}",
    //             changeEvent.getEventType(),
    //             changeEvent.getEntityType(),
    //             changeEvent.getEntityId());
    // }
}

