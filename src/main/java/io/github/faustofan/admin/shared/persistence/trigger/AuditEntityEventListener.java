package io.github.faustofan.admin.shared.persistence.trigger;

import io.github.faustofan.admin.shared.persistence.AuditEntity;
import io.github.faustofan.admin.shared.persistence.event.EntityChangeEvent;
import io.github.faustofan.admin.shared.persistence.event.EntityEventPublisher;
import io.github.faustofan.admin.shared.persistence.event.EntityEventType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.babyfish.jimmer.sql.event.EntityEvent;
import org.jboss.logging.Logger;

/**
 * 审计实体事件监听器
 * <p>
 * 监听所有继承自 {@link AuditEntity} 的实体变更事件（INSERT/UPDATE/DELETE），
 * 并将其转换为 {@link EntityChangeEvent} 发布到消息总线。
 * <p>
 * <b>设计说明：</b>
 * <ul>
 *     <li>使用 Jimmer 的 Transaction Trigger 机制，在事务内触发事件</li>
 *     <li>通过 {@link EntityEventPublisher} 发布事件，与现有消息基础设施集成</li>
 *     <li>仅处理 AuditEntity 及其子类的变更</li>
 * </ul>
 */
@ApplicationScoped
public class AuditEntityEventListener {

    private static final Logger LOG = Logger.getLogger(AuditEntityEventListener.class);

    private static final String SOURCE = "JimmerTrigger";

    private final EntityEventPublisher eventPublisher;

    @Inject
    public AuditEntityEventListener(EntityEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * 处理 Jimmer EntityEvent
     * <p>
     * 此方法由 {@link JimmerTriggerConfiguration} 注册为触发器回调
     *
     * @param event Jimmer 实体事件
     * @param <E>   实体类型
     */
    @SuppressWarnings("unchecked")
    public <E> void onEntityChanged(EntityEvent<E> event) {
        Class<?> entityClass = event.getImmutableType().getJavaClass();

        // 只处理 AuditEntity 的子类
        if (!AuditEntity.class.isAssignableFrom(entityClass)) {
            return;
        }

        try {
            EntityChangeEvent<E> changeEvent = convertToChangeEvent(event);
            if (changeEvent != null) {
                eventPublisher.publish(changeEvent);
            }
        } catch (Exception e) {
            LOG.errorv(e, "Error processing entity event for {0}", entityClass.getSimpleName());
            // 不抛出异常，避免影响主事务
        }
    }

    /**
     * 将 Jimmer EntityEvent 转换为 EntityChangeEvent
     */
    @SuppressWarnings("unchecked")
    private <E> EntityChangeEvent<E> convertToChangeEvent(EntityEvent<E> event) {
        E oldEntity = event.getOldEntity();
        E newEntity = event.getNewEntity();
        Object entityId = event.getId();

        if (oldEntity == null && newEntity != null) {
            // INSERT
            return EntityChangeEvent.inserted(newEntity, entityId, SOURCE);
        } else if (oldEntity != null && newEntity != null) {
            // UPDATE
            return EntityChangeEvent.updated(oldEntity, newEntity, entityId, SOURCE);
        } else if (oldEntity != null && newEntity == null) {
            // DELETE
            return EntityChangeEvent.deleted(oldEntity, entityId, SOURCE);
        }

        // 理论上不应该到达这里
        LOG.warnv("Unexpected entity event state: oldEntity={0}, newEntity={1}",
                oldEntity != null, newEntity != null);
        return null;
    }

    /**
     * 判断事件类型
     */
    private EntityEventType determineEventType(EntityEvent<?> event) {
        if (event.getOldEntity() == null) {
            return EntityEventType.INSERTED;
        } else if (event.getNewEntity() == null) {
            return EntityEventType.DELETED;
        } else {
            return EntityEventType.UPDATED;
        }
    }
}
