package io.github.faustofan.admin.shared.persistence.event;

import io.github.faustofan.admin.shared.messaging.constants.EventType;
import io.github.faustofan.admin.shared.messaging.constants.MessagingConstants;
import io.github.faustofan.admin.shared.messaging.core.DomainEvent;
import io.github.faustofan.admin.shared.messaging.facade.MessagingFacade;
import io.github.faustofan.admin.shared.persistence.AuditEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * 实体事件发布器
 * <p>
 * 负责将 {@link EntityChangeEvent} 转换为 {@link DomainEvent} 并通过 {@link MessagingFacade} 发布。
 * <p>
 * 设计目标：
 * <ul>
 *     <li>将 Jimmer 触发器事件转换为统一的领域事件</li>
 *     <li>支持过滤，只发布继承自 {@link AuditEntity} 的实体事件</li>
 *     <li>自动生成正确的 Topic 和事件类型</li>
 * </ul>
 */
@ApplicationScoped
public class EntityEventPublisher {

    private static final Logger LOG = Logger.getLogger(EntityEventPublisher.class);

    private final MessagingFacade messagingFacade;

    @Inject
    public EntityEventPublisher(MessagingFacade messagingFacade) {
        this.messagingFacade = messagingFacade;
    }

    /**
     * 发布实体变更事件
     *
     * @param event 实体变更事件
     * @param <E>   实体类型
     */
    public <E> void publish(EntityChangeEvent<E> event) {
        // 只处理 AuditEntity 的子类
        if (!AuditEntity.class.isAssignableFrom(event.getEntityClass())) {
            LOG.debugv("Skipping event for non-AuditEntity: {0}", event.getEntityType());
            return;
        }

        try {
            // 构造领域事件
            DomainEvent<EntityChangeEvent<E>> domainEvent = createDomainEvent(event);

            // 发布到消息总线
            messagingFacade.publish(domainEvent);

            LOG.debugv("Published entity event: {0} for {1}#{2}",
                    event.getEventType(),
                    event.getEntityType(),
                    event.getEntityId());

        } catch (Exception e) {
            LOG.errorv(e, "Failed to publish entity event: {0}", event);
            // 不抛出异常，避免影响主业务流程
        }
    }

    /**
     * 异步发布实体变更事件
     *
     * @param event 实体变更事件
     * @param <E>   实体类型
     */
    public <E> void publishAsync(EntityChangeEvent<E> event) {
        // 只处理 AuditEntity 的子类
        if (!AuditEntity.class.isAssignableFrom(event.getEntityClass())) {
            return;
        }

        try {
            DomainEvent<EntityChangeEvent<E>> domainEvent = createDomainEvent(event);

            messagingFacade.publishAsync(domainEvent)
                    .exceptionally(ex -> {
                        LOG.errorv(ex, "Async publish failed for entity event: {0}", event);
                        return null;
                    });

        } catch (Exception e) {
            LOG.errorv(e, "Failed to publish entity event async: {0}", event);
        }
    }

    /**
     * 创建领域事件
     */
    private <E> DomainEvent<EntityChangeEvent<E>> createDomainEvent(EntityChangeEvent<E> event) {
        // 转换为统一的 EventType
        EventType eventType = mapToEventType(event.getEventType());

        // 生成 Topic: admin.domain.EntitySimpleName
        String entitySimpleName = event.getEntityClass().getSimpleName();
        String topic = MessagingConstants.TopicPrefix.DOMAIN + entitySimpleName.toLowerCase();

        // 使用 DomainEvent.of 工厂方法
        return DomainEvent.of(
                String.valueOf(event.getEntityId()),
                entitySimpleName,
                eventType,
                event
        );
    }

    /**
     * 将实体事件类型映射为消息事件类型
     */
    private EventType mapToEventType(EntityEventType entityEventType) {
        return switch (entityEventType) {
            case INSERTED -> EventType.CREATED;
            case UPDATED -> EventType.UPDATED;
            case DELETED -> EventType.DELETED;
        };
    }
}
