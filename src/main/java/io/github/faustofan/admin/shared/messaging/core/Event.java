package io.github.faustofan.admin.shared.messaging.core;

import io.github.faustofan.admin.shared.messaging.constants.ChannelType;
import io.github.faustofan.admin.shared.messaging.constants.DeliveryMode;
import io.github.faustofan.admin.shared.messaging.constants.EventType;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

/**
 * 事件接口 - 所有事件的基础契约
 * <p>
 * 定义事件的基本属性，所有领域事件、集成事件等都应实现此接口
 *
 * @param <T> 事件负载类型
 */
public interface Event<T> extends Serializable {

    /**
     * 获取事件唯一标识
     */
    String getEventId();

    /**
     * 获取事件类型
     */
    EventType getEventType();

    /**
     * 获取事件源（如：user、order 等）
     */
    String getSource();

    /**
     * 获取事件主题/Topic
     */
    String getTopic();

    /**
     * 获取事件发生时间
     */
    Instant getOccurredAt();

    /**
     * 获取事件负载数据
     */
    T getPayload();

    /**
     * 获取事件元数据（如：traceId、tenantId 等）
     */
    Map<String, String> getMetadata();

    /**
     * 获取追踪ID
     */
    default String getTraceId() {
        return getMetadata() != null ? getMetadata().get("traceId") : null;
    }

    /**
     * 获取租户ID
     */
    default Long getTenantId() {
        String tid = getMetadata() != null ? getMetadata().get("tenantId") : null;
        return tid != null ? Long.parseLong(tid) : null;
    }

    /**
     * 获取推荐的通道类型
     */
    default ChannelType getPreferredChannel() {
        return ChannelType.AUTO;
    }

    /**
     * 获取投递模式
     */
    default DeliveryMode getDeliveryMode() {
        return DeliveryMode.AT_LEAST_ONCE;
    }
}
