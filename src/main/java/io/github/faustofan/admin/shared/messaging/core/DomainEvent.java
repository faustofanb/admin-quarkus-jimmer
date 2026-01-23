package io.github.faustofan.admin.shared.messaging.core;

import io.github.faustofan.admin.shared.messaging.constants.EventType;
import io.github.faustofan.admin.shared.messaging.constants.MessagingConstants;

import java.util.Map;

/**
 * 领域事件
 * <p>
 * 表示领域层发生的业务事件，通常用于领域模型内部的事件驱动
 *
 * @param <T> 事件负载类型
 */
public class DomainEvent<T> extends BaseEvent<T> {

    /**
     * 聚合根ID
     */
    private final String aggregateId;

    /**
     * 聚合根类型
     */
    private final String aggregateType;

    /**
     * 聚合版本号
     */
    private final Long version;

    protected DomainEvent(String aggregateId, String aggregateType, EventType eventType, T payload) {
        super(eventType, aggregateType, MessagingConstants.TopicPrefix.DOMAIN + aggregateType, payload);
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.version = null;
    }

    protected DomainEvent(String aggregateId, String aggregateType, Long version,
                          EventType eventType, T payload) {
        super(eventType, aggregateType, MessagingConstants.TopicPrefix.DOMAIN + aggregateType, payload);
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.version = version;
    }

    protected DomainEvent(String aggregateId, String aggregateType, Long version,
                          EventType eventType, String topic, T payload, Map<String, String> metadata) {
        super(null, eventType, aggregateType, topic, payload, metadata);
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.version = version;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public Long getVersion() {
        return version;
    }

    // ===========================
    // 工厂方法
    // ===========================

    /**
     * 创建领域事件
     */
    public static <T> DomainEvent<T> of(String aggregateId, String aggregateType, EventType eventType, T payload) {
        return new DomainEvent<>(aggregateId, aggregateType, eventType, payload);
    }

    /**
     * 创建实体创建事件
     */
    public static <T> DomainEvent<T> created(String aggregateId, String aggregateType, T payload) {
        return new DomainEvent<>(aggregateId, aggregateType, EventType.CREATED, payload);
    }

    /**
     * 创建实体更新事件
     */
    public static <T> DomainEvent<T> updated(String aggregateId, String aggregateType, T payload) {
        return new DomainEvent<>(aggregateId, aggregateType, EventType.UPDATED, payload);
    }

    /**
     * 创建实体删除事件
     */
    public static <T> DomainEvent<T> deleted(String aggregateId, String aggregateType, T payload) {
        return new DomainEvent<>(aggregateId, aggregateType, EventType.DELETED, payload);
    }

    @Override
    public String toString() {
        return "DomainEvent{" +
                "eventId='" + getEventId() + '\'' +
                ", aggregateId='" + aggregateId + '\'' +
                ", aggregateType='" + aggregateType + '\'' +
                ", version=" + version +
                ", eventType=" + getEventType() +
                ", occurredAt=" + getOccurredAt() +
                '}';
    }
}
