package io.github.faustofan.admin.shared.messaging.core;

import io.github.faustofan.admin.shared.messaging.constants.ChannelType;
import io.github.faustofan.admin.shared.messaging.constants.DeliveryMode;
import io.github.faustofan.admin.shared.messaging.constants.EventType;
import io.github.faustofan.admin.shared.messaging.constants.MessagingConstants;

import java.io.Serial;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 基础事件抽象类
 * <p>
 * 提供 Event 接口的默认实现，业务事件可以继承此类
 *
 * @param <T> 事件负载类型
 */
public abstract class BaseEvent<T> implements Event<T> {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String eventId;
    private final EventType eventType;
    private final String source;
    private final String topic;
    private final Instant occurredAt;
    private final T payload;
    private final Map<String, String> metadata;

    /**
     * 构造事件（完整参数）
     */
    protected BaseEvent(String eventId, EventType eventType, String source, String topic,
                        T payload, Map<String, String> metadata) {
        this.eventId = eventId != null ? eventId : UUID.randomUUID().toString();
        this.eventType = eventType;
        this.source = source;
        this.topic = topic;
        this.occurredAt = Instant.now();
        this.payload = payload;
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }

    /**
     * 构造事件（简化参数）
     */
    protected BaseEvent(EventType eventType, String source, String topic, T payload) {
        this(null, eventType, source, topic, payload, null);
    }

    /**
     * 构造事件（使用默认Topic）
     */
    protected BaseEvent(EventType eventType, String source, T payload) {
        this(null, eventType, source, buildDefaultTopic(source, eventType), payload, null);
    }

    @Override
    public String getEventId() {
        return eventId;
    }

    @Override
    public EventType getEventType() {
        return eventType;
    }

    @Override
    public String getSource() {
        return source;
    }

    @Override
    public String getTopic() {
        return topic;
    }

    @Override
    public Instant getOccurredAt() {
        return occurredAt;
    }

    @Override
    public T getPayload() {
        return payload;
    }

    @Override
    public Map<String, String> getMetadata() {
        return metadata;
    }

    /**
     * 添加元数据
     */
    public BaseEvent<T> withMetadata(String key, String value) {
        this.metadata.put(key, value);
        return this;
    }

    /**
     * 添加追踪ID
     */
    public BaseEvent<T> withTraceId(String traceId) {
        return withMetadata("traceId", traceId);
    }

    /**
     * 添加租户ID
     */
    public BaseEvent<T> withTenantId(Long tenantId) {
        return withMetadata("tenantId", String.valueOf(tenantId));
    }

    /**
     * 添加用户ID
     */
    public BaseEvent<T> withUserId(Long userId) {
        return withMetadata("userId", String.valueOf(userId));
    }

    /**
     * 构建默认Topic
     */
    private static String buildDefaultTopic(String source, EventType eventType) {
        return MessagingConstants.TopicPrefix.DOMAIN + source + MessagingConstants.TOPIC_SEPARATOR + eventType.getCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "eventId='" + eventId + '\'' +
                ", eventType=" + eventType +
                ", source='" + source + '\'' +
                ", topic='" + topic + '\'' +
                ", occurredAt=" + occurredAt +
                '}';
    }
}
