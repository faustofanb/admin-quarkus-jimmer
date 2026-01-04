package io.github.faustofan.admin.shared.persistence.event;

import org.babyfish.jimmer.meta.ImmutableType;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * 实体变更事件
 * <p>
 * 封装实体生命周期变更的详细信息，包括：
 * <ul>
 *     <li>事件 ID 和时间戳</li>
 *     <li>实体类型和标识</li>
 *     <li>变更前后的实体快照</li>
 *     <li>可选的元数据</li>
 * </ul>
 *
 * @param <E> 实体类型
 */
public class EntityChangeEvent<E> {

    /**
     * 事件唯一标识
     */
    private final String eventId;

    /**
     * 事件发生时间
     */
    private final Instant occurredAt;

    /**
     * 事件类型
     */
    private final EntityEventType eventType;

    /**
     * 实体类型（全限定类名）
     */
    private final String entityType;

    /**
     * 实体类
     */
    private final Class<E> entityClass;

    /**
     * 实体 ID
     */
    private final Object entityId;

    /**
     * 变更前的实体（对于 INSERT 为 null）
     */
    private final E oldEntity;

    /**
     * 变更后的实体（对于 DELETE 为 null）
     */
    private final E newEntity;

    /**
     * 事件来源（服务名/模块名）
     */
    private final String source;

    /**
     * 元数据（可扩展）
     */
    private final Map<String, String> metadata;

    private EntityChangeEvent(Builder<E> builder) {
        this.eventId = builder.eventId != null ? builder.eventId : UUID.randomUUID().toString();
        this.occurredAt = builder.occurredAt != null ? builder.occurredAt : Instant.now();
        this.eventType = builder.eventType;
        this.entityType = builder.entityType;
        this.entityClass = builder.entityClass;
        this.entityId = builder.entityId;
        this.oldEntity = builder.oldEntity;
        this.newEntity = builder.newEntity;
        this.source = builder.source;
        this.metadata = builder.metadata != null ? Collections.unmodifiableMap(builder.metadata) : Collections.emptyMap();
    }

    // ===========================
    // Getters
    // ===========================

    public String getEventId() {
        return eventId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public EntityEventType getEventType() {
        return eventType;
    }

    public String getEntityType() {
        return entityType;
    }

    public Class<E> getEntityClass() {
        return entityClass;
    }

    public Object getEntityId() {
        return entityId;
    }

    public E getOldEntity() {
        return oldEntity;
    }

    public E getNewEntity() {
        return newEntity;
    }

    public String getSource() {
        return source;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    /**
     * 判断是否为插入事件
     */
    public boolean isInsert() {
        return eventType == EntityEventType.INSERTED;
    }

    /**
     * 判断是否为更新事件
     */
    public boolean isUpdate() {
        return eventType == EntityEventType.UPDATED;
    }

    /**
     * 判断是否为删除事件
     */
    public boolean isDelete() {
        return eventType == EntityEventType.DELETED;
    }

    /**
     * 获取当前有效实体（INSERT/UPDATE 返回新实体，DELETE 返回旧实体）
     */
    public E getEntity() {
        return newEntity != null ? newEntity : oldEntity;
    }

    // ===========================
    // 工厂方法
    // ===========================

    /**
     * 创建 INSERT 事件
     */
    @SuppressWarnings("unchecked")
    public static <E> EntityChangeEvent<E> inserted(E entity, Object entityId, String source) {
        Class<E> entityClass = (Class<E>) ImmutableType.get(entity.getClass()).getJavaClass();
        return new Builder<E>()
                .eventType(EntityEventType.INSERTED)
                .entityClass(entityClass)
                .entityType(entityClass.getName())
                .entityId(entityId)
                .newEntity(entity)
                .source(source)
                .build();
    }

    /**
     * 创建 UPDATE 事件
     */
    @SuppressWarnings("unchecked")
    public static <E> EntityChangeEvent<E> updated(E oldEntity, E newEntity, Object entityId, String source) {
        Class<E> entityClass = (Class<E>) ImmutableType.get(newEntity.getClass()).getJavaClass();
        return new Builder<E>()
                .eventType(EntityEventType.UPDATED)
                .entityClass(entityClass)
                .entityType(entityClass.getName())
                .entityId(entityId)
                .oldEntity(oldEntity)
                .newEntity(newEntity)
                .source(source)
                .build();
    }

    /**
     * 创建 DELETE 事件
     */
    @SuppressWarnings("unchecked")
    public static <E> EntityChangeEvent<E> deleted(E entity, Object entityId, String source) {
        Class<E> entityClass = (Class<E>) ImmutableType.get(entity.getClass()).getJavaClass();
        return new Builder<E>()
                .eventType(EntityEventType.DELETED)
                .entityClass(entityClass)
                .entityType(entityClass.getName())
                .entityId(entityId)
                .oldEntity(entity)
                .source(source)
                .build();
    }

    /**
     * 获取构建器
     */
    public static <E> Builder<E> builder() {
        return new Builder<>();
    }

    // ===========================
    // Builder
    // ===========================

    public static class Builder<E> {
        private String eventId;
        private Instant occurredAt;
        private EntityEventType eventType;
        private String entityType;
        private Class<E> entityClass;
        private Object entityId;
        private E oldEntity;
        private E newEntity;
        private String source;
        private Map<String, String> metadata;

        public Builder<E> eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder<E> occurredAt(Instant occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        public Builder<E> eventType(EntityEventType eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder<E> entityType(String entityType) {
            this.entityType = entityType;
            return this;
        }

        public Builder<E> entityClass(Class<E> entityClass) {
            this.entityClass = entityClass;
            return this;
        }

        public Builder<E> entityId(Object entityId) {
            this.entityId = entityId;
            return this;
        }

        public Builder<E> oldEntity(E oldEntity) {
            this.oldEntity = oldEntity;
            return this;
        }

        public Builder<E> newEntity(E newEntity) {
            this.newEntity = newEntity;
            return this;
        }

        public Builder<E> source(String source) {
            this.source = source;
            return this;
        }

        public Builder<E> metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public EntityChangeEvent<E> build() {
            return new EntityChangeEvent<>(this);
        }
    }

    @Override
    public String toString() {
        return "EntityChangeEvent{" +
                "eventId='" + eventId + '\'' +
                ", eventType=" + eventType +
                ", entityType='" + entityType + '\'' +
                ", entityId=" + entityId +
                ", occurredAt=" + occurredAt +
                ", source='" + source + '\'' +
                '}';
    }
}
