package io.github.faustofan.admin.shared.messaging.local;

import io.github.faustofan.admin.shared.messaging.core.Event;

import java.io.Serial;
import java.io.Serializable;

/**
 * 本地事件包装器
 * <p>
 * 用于包装事件以便在 CDI 事件系统中传播
 *
 * @param <T> 事件负载类型
 */
public class LocalEventWrapper<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Event<T> event;
    private final String topic;

    public LocalEventWrapper(Event<T> event) {
        this.event = event;
        this.topic = event.getTopic();
    }

    public Event<T> getEvent() {
        return event;
    }

    public String getTopic() {
        return topic;
    }

    /**
     * 获取事件ID
     */
    public String getEventId() {
        return event.getEventId();
    }

    /**
     * 获取事件负载
     */
    public T getPayload() {
        return event.getPayload();
    }

    /**
     * 检查是否匹配指定Topic
     */
    public boolean matchesTopic(String targetTopic) {
        return topic != null && topic.equals(targetTopic);
    }

    /**
     * 检查是否匹配Topic前缀
     */
    public boolean matchesTopicPrefix(String prefix) {
        return topic != null && topic.startsWith(prefix);
    }

    @Override
    public String toString() {
        return "LocalEventWrapper{" +
                "eventId='" + event.getEventId() + '\'' +
                ", topic='" + topic + '\'' +
                ", eventType=" + event.getEventType() +
                '}';
    }
}
