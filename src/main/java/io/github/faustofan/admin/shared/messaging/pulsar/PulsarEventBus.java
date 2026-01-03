package io.github.faustofan.admin.shared.messaging.pulsar;

import io.github.faustofan.admin.shared.messaging.config.MessagingConfig;
import io.github.faustofan.admin.shared.messaging.constants.ChannelType;
import io.github.faustofan.admin.shared.messaging.core.*;
import io.github.faustofan.admin.shared.messaging.exception.MessagingException;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pulsar 事件总线实现
 * <p>
 * 可插拔的 Pulsar 消息队列集成，通过配置开关控制是否启用。
 * <p>
 * 特性：
 * <ul>
 *   <li>可插拔设计 - 通过 admin.messaging.pulsar.enabled 控制</li>
 *   <li>分布式消息传递</li>
 *   <li>支持延迟消息</li>
 *   <li>支持批量发送</li>
 *   <li>响应式流订阅</li>
 * </ul>
 * <p>
 * 注意：使用前需要在 pom.xml 中启用 quarkus-messaging-pulsar 依赖
 */
@ApplicationScoped
public class PulsarEventBus implements EventBus {

    private static final Logger LOG = Logger.getLogger(PulsarEventBus.class);

    private final MessagingConfig config;

    // Pulsar 相关的依赖将通过 CDI 注入（当依赖可用时）
    // 这里使用简化的实现，实际项目中应该注入 Pulsar Client

    // 用于模拟流订阅
    private final Map<String, BroadcastProcessor<Event<?>>> topicProcessors = new ConcurrentHashMap<>();

    @Inject
    public PulsarEventBus(MessagingConfig config) {
        this.config = config;
        if (config.pulsar().enabled()) {
            LOG.info("Pulsar event bus initialized with service URL: " + config.pulsar().serviceUrl());
        } else {
            LOG.info("Pulsar event bus is disabled");
        }
    }

    // ===========================
    // EventBus 接口实现
    // ===========================

    @Override
    public <T> void publish(Event<T> event) {
        if (!isAvailable()) {
            throw MessagingException.channelUnavailable(ChannelType.PULSAR,
                    "Pulsar is not enabled. Set admin.messaging.pulsar.enabled=true");
        }

        try {
            LOG.debugv("Publishing event to Pulsar: {0} -> {1}", event.getEventId(), event.getTopic());
            doPublish(event);
            broadcastToSubscribers(event);
        } catch (Exception e) {
            throw MessagingException.sendFailed(event.getTopic(), e);
        }
    }

    @Override
    public <T> void publish(String topic, Event<T> event) {
        if (!isAvailable()) {
            throw MessagingException.channelUnavailable(ChannelType.PULSAR, "Pulsar is not enabled");
        }

        try {
            LOG.debugv("Publishing event to Pulsar topic: {0}", topic);
            doPublishToTopic(topic, event);
        } catch (Exception e) {
            throw MessagingException.sendFailed(topic, e);
        }
    }

    @Override
    public <T> CompletionStage<Void> publishAsync(Event<T> event) {
        if (!isAvailable()) {
            return CompletableFuture.failedFuture(
                    MessagingException.channelUnavailable(ChannelType.PULSAR, "Pulsar is not enabled"));
        }

        return CompletableFuture.runAsync(() -> {
            doPublish(event);
            broadcastToSubscribers(event);
        });
    }

    @Override
    public <T> Uni<Void> publishUni(Event<T> event) {
        return Uni.createFrom().completionStage(() -> publishAsync(event));
    }

    @Override
    public <T> void send(Message<T> message) {
        if (!isAvailable()) {
            throw MessagingException.channelUnavailable(ChannelType.PULSAR, "Pulsar is not enabled");
        }

        try {
            LOG.debugv("Sending message to Pulsar: {0} -> {1}", message.getMessageId(), message.getTopic());
            doSendMessage(message);
        } catch (Exception e) {
            throw MessagingException.sendFailed(message.getTopic(), e);
        }
    }

    @Override
    public <T> CompletionStage<Void> sendAsync(Message<T> message) {
        if (!isAvailable()) {
            return CompletableFuture.failedFuture(
                    MessagingException.channelUnavailable(ChannelType.PULSAR, "Pulsar is not enabled"));
        }

        return CompletableFuture.runAsync(() -> doSendMessage(message));
    }

    @Override
    public <T> Uni<Void> sendUni(Message<T> message) {
        return Uni.createFrom().completionStage(() -> sendAsync(message));
    }

    @Override
    public <T> void fire(Event<T> event) {
        if (!isAvailable()) {
            LOG.warnv("Pulsar is not enabled, skipping fire: {0}", event.getEventId());
            return;
        }

        // Fire and forget
        publishAsync(event).exceptionally(ex -> {
            LOG.errorv(ex, "Failed to fire event to Pulsar: {0}", event.getEventId());
            return null;
        });
    }

    @Override
    public <T> void fire(String topic, Event<T> event) {
        if (!isAvailable()) {
            LOG.warnv("Pulsar is not enabled, skipping fire to topic: {0}", topic);
            return;
        }

        CompletableFuture.runAsync(() -> doPublishToTopic(topic, event))
                .exceptionally(ex -> {
                    LOG.errorv(ex, "Failed to fire event to Pulsar topic: {0}", topic);
                    return null;
                });
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Multi<Event<T>> subscribe(String topic, Class<T> eventType) {
        if (!isAvailable()) {
            return Multi.createFrom().empty();
        }

        BroadcastProcessor<Event<?>> processor =
                topicProcessors.computeIfAbsent(topic, k -> {
                    BroadcastProcessor<Event<?>> bp = BroadcastProcessor.create();
                    // 这里应该启动 Pulsar Consumer 订阅
                    startConsumer(topic, bp);
                    return bp;
                });

        return processor
                .filter(event -> topic.equals(event.getTopic()))
                .map(event -> (Event<T>) event);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Multi<Message<T>> subscribeMessages(String topic, Class<T> payloadType) {
        // 将订阅的事件转换为消息
        return subscribe(topic, payloadType)
                .map(event -> Message.of(topic, (T) event.getPayload()));
    }

    @Override
    public ChannelType getChannelType() {
        return ChannelType.PULSAR;
    }

    @Override
    public boolean isAvailable() {
        return config.enabled() && config.pulsar().enabled();
    }

    // ===========================
    // 内部方法 - Pulsar 操作
    // ===========================

    /**
     * 实际发布事件到 Pulsar
     * <p>
     * 注意：这是简化实现，实际项目中应该使用 Pulsar Client
     */
    private <T> void doPublish(Event<T> event) {
        // TODO: 当启用 quarkus-messaging-pulsar 依赖后，替换为实际的 Pulsar 发送逻辑
        // 示例：
        // pulsarClient.newProducer()
        //     .topic(buildFullTopic(event.getTopic()))
        //     .send(serialize(event));

        LOG.debugv("Pulsar publish (simulated): topic={0}, eventId={1}",
                event.getTopic(), event.getEventId());
    }

    private <T> void doPublishToTopic(String topic, Event<T> event) {
        LOG.debugv("Pulsar publish to topic (simulated): topic={0}, eventId={1}",
                topic, event.getEventId());
    }

    private <T> void doSendMessage(Message<T> message) {
        // TODO: 实际的 Pulsar 消息发送
        LOG.debugv("Pulsar send message (simulated): topic={0}, messageId={1}",
                message.getTopic(), message.getMessageId());
    }

    private void startConsumer(String topic, BroadcastProcessor<Event<?>> processor) {
        // TODO: 启动 Pulsar Consumer
        LOG.debugv("Starting Pulsar consumer for topic (simulated): {0}", topic);
    }

    private <T> void broadcastToSubscribers(Event<T> event) {
        String topic = event.getTopic();
        BroadcastProcessor<Event<?>> processor = topicProcessors.get(topic);
        if (processor != null) {
            processor.onNext(event);
        }
    }

    /**
     * 构建完整的 Pulsar Topic 名称
     */
    public String buildFullTopic(String topic) {
        return String.format("persistent://%s/%s/%s",
                config.pulsar().tenant(),
                config.pulsar().namespace(),
                topic);
    }

    /**
     * 获取订阅名称
     */
    public String getSubscriptionName(String topic) {
        return config.pulsar().subscriptionPrefix() + topic.replace(".", "-");
    }
}
