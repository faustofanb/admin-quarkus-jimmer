package io.github.faustofan.admin.shared.messaging.core;

import io.github.faustofan.admin.shared.messaging.constants.MessagingConstants;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 消息包装器
 * <p>
 * 封装事件及其传输元数据，用于在不同通道间传输
 *
 * @param <T> 负载类型
 */
public class Message<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 消息唯一ID
     */
    private final String messageId;

    /**
     * 消息Topic
     */
    private final String topic;

    /**
     * 消息键（用于分区）
     */
    private final String key;

    /**
     * 消息负载
     */
    private final T payload;

    /**
     * 消息头
     */
    private final Map<String, String> headers;

    /**
     * 创建时间
     */
    private final Instant createdAt;

    /**
     * 延迟发送时间（可选）
     */
    private final Instant deliverAt;

    private Message(String messageId, String topic, String key, T payload,
                    Map<String, String> headers, Instant deliverAt) {
        this.messageId = messageId != null ? messageId : UUID.randomUUID().toString();
        this.topic = topic;
        this.key = key;
        this.payload = payload;
        this.headers = headers != null ? new HashMap<>(headers) : new HashMap<>();
        this.createdAt = Instant.now();
        this.deliverAt = deliverAt;

        // 自动添加基础头信息
        this.headers.putIfAbsent(MessagingConstants.Header.MESSAGE_ID, this.messageId);
        this.headers.putIfAbsent(MessagingConstants.Header.TIMESTAMP, String.valueOf(this.createdAt.toEpochMilli()));
        this.headers.putIfAbsent(MessagingConstants.Header.SOURCE, MessagingConstants.APPLICATION_NAME);
    }

    public String getMessageId() {
        return messageId;
    }

    public String getTopic() {
        return topic;
    }

    public String getKey() {
        return key;
    }

    public T getPayload() {
        return payload;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeliverAt() {
        return deliverAt;
    }

    public String getHeader(String name) {
        return headers.get(name);
    }

    public String getTraceId() {
        return headers.get(MessagingConstants.Header.TRACE_ID);
    }

    public String getTenantId() {
        return headers.get(MessagingConstants.Header.TENANT_ID);
    }

    public boolean isDelayed() {
        return deliverAt != null && deliverAt.isAfter(Instant.now());
    }

    // ===========================
    // Builder 模式
    // ===========================

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static <T> Message<T> of(String topic, T payload) {
        return Message.<T>builder()
                .topic(topic)
                .payload(payload)
                .build();
    }

    public static <T> Message<T> of(String topic, String key, T payload) {
        return Message.<T>builder()
                .topic(topic)
                .key(key)
                .payload(payload)
                .build();
    }

    /**
     * 从事件创建消息
     */
    public static <T> Message<Event<T>> fromEvent(Event<T> event) {
        Builder<Event<T>> builder = Message.<Event<T>>builder()
                .topic(event.getTopic())
                .payload(event)
                .header(MessagingConstants.Header.EVENT_TYPE, event.getEventType().getCode());

        // 复制事件元数据到消息头
        if (event.getMetadata() != null) {
            event.getMetadata().forEach(builder::header);
        }

        return builder.build();
    }

    public static class Builder<T> {
        private String messageId;
        private String topic;
        private String key;
        private T payload;
        private final Map<String, String> headers = new HashMap<>();
        private Instant deliverAt;

        public Builder<T> messageId(String messageId) {
            this.messageId = messageId;
            return this;
        }

        public Builder<T> topic(String topic) {
            this.topic = topic;
            return this;
        }

        public Builder<T> key(String key) {
            this.key = key;
            return this;
        }

        public Builder<T> payload(T payload) {
            this.payload = payload;
            return this;
        }

        public Builder<T> header(String name, String value) {
            this.headers.put(name, value);
            return this;
        }

        public Builder<T> headers(Map<String, String> headers) {
            this.headers.putAll(headers);
            return this;
        }

        public Builder<T> traceId(String traceId) {
            return header(MessagingConstants.Header.TRACE_ID, traceId);
        }

        public Builder<T> tenantId(Long tenantId) {
            return header(MessagingConstants.Header.TENANT_ID, String.valueOf(tenantId));
        }

        public Builder<T> userId(Long userId) {
            return header(MessagingConstants.Header.USER_ID, String.valueOf(userId));
        }

        public Builder<T> deliverAt(Instant deliverAt) {
            this.deliverAt = deliverAt;
            return this;
        }

        public Builder<T> delaySeconds(long seconds) {
            this.deliverAt = Instant.now().plusSeconds(seconds);
            return this;
        }

        public Message<T> build() {
            if (topic == null || topic.isBlank()) {
                throw new IllegalArgumentException("Topic is required");
            }
            return new Message<>(messageId, topic, key, payload, headers, deliverAt);
        }
    }

    @Override
    public String toString() {
        return "Message{" +
                "messageId='" + messageId + '\'' +
                ", topic='" + topic + '\'' +
                ", key='" + key + '\'' +
                ", createdAt=" + createdAt +
                ", delayed=" + isDelayed() +
                '}';
    }
}
