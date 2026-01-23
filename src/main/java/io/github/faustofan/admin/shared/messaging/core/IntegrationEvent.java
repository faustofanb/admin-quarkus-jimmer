package io.github.faustofan.admin.shared.messaging.core;

import io.github.faustofan.admin.shared.messaging.constants.ChannelType;
import io.github.faustofan.admin.shared.messaging.constants.DeliveryMode;
import io.github.faustofan.admin.shared.messaging.constants.EventType;
import io.github.faustofan.admin.shared.messaging.constants.MessagingConstants;

import java.util.Map;

/**
 * 集成事件
 * <p>
 * 用于跨服务/微服务边界的事件传播，通过消息队列（如 Pulsar）发送
 *
 * @param <T> 事件负载类型
 */
public class IntegrationEvent<T> extends BaseEvent<T> {

    /**
     * 目标服务
     */
    private final String targetService;

    /**
     * 重试次数
     */
    private final int retryCount;

    /**
     * 优先级
     */
    private final int priority;

    protected IntegrationEvent(EventType eventType, String source, String topic, T payload,
                               String targetService, int retryCount, int priority,
                               Map<String, String> metadata) {
        super(null, eventType, source, topic, payload, metadata);
        this.targetService = targetService;
        this.retryCount = retryCount;
        this.priority = priority;
    }

    public String getTargetService() {
        return targetService;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public int getPriority() {
        return priority;
    }

    @Override
    public ChannelType getPreferredChannel() {
        return ChannelType.PULSAR;
    }

    @Override
    public DeliveryMode getDeliveryMode() {
        return DeliveryMode.AT_LEAST_ONCE;
    }

    // ===========================
    // Builder 模式
    // ===========================

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static class Builder<T> {
        private EventType eventType = EventType.CUSTOM;
        private String source = MessagingConstants.APPLICATION_NAME;
        private String topic;
        private T payload;
        private String targetService;
        private int retryCount = MessagingConstants.DEFAULT_RETRY_COUNT;
        private int priority = 0;
        private Map<String, String> metadata;

        public Builder<T> eventType(EventType eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder<T> source(String source) {
            this.source = source;
            return this;
        }

        public Builder<T> topic(String topic) {
            this.topic = topic;
            return this;
        }

        public Builder<T> payload(T payload) {
            this.payload = payload;
            return this;
        }

        public Builder<T> targetService(String targetService) {
            this.targetService = targetService;
            return this;
        }

        public Builder<T> retryCount(int retryCount) {
            this.retryCount = retryCount;
            return this;
        }

        public Builder<T> priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder<T> metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public IntegrationEvent<T> build() {
            if (topic == null) {
                topic = MessagingConstants.TopicPrefix.INTEGRATION + source;
            }
            return new IntegrationEvent<>(eventType, source, topic, payload,
                    targetService, retryCount, priority, metadata);
        }
    }

    @Override
    public String toString() {
        return "IntegrationEvent{" +
                "eventId='" + getEventId() + '\'' +
                ", eventType=" + getEventType() +
                ", source='" + getSource() + '\'' +
                ", topic='" + getTopic() + '\'' +
                ", targetService='" + targetService + '\'' +
                ", retryCount=" + retryCount +
                ", priority=" + priority +
                ", occurredAt=" + getOccurredAt() +
                '}';
    }
}
