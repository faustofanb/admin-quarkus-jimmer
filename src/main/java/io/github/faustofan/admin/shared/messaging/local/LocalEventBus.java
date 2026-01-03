package io.github.faustofan.admin.shared.messaging.local;

import io.github.faustofan.admin.shared.messaging.config.MessagingConfig;
import io.github.faustofan.admin.shared.messaging.constants.ChannelType;
import io.github.faustofan.admin.shared.messaging.core.*;
import io.github.faustofan.admin.shared.messaging.exception.MessagingException;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import jakarta.enterprise.context.ApplicationScoped;

import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地事件总线实现
 * <p>
 * 基于 Quarkus CDI Events 实现的本地事件总线，事件仅在当前 JVM 内传播。
 * <p>
 * 特性：
 * <ul>
 *   <li>同步和异步事件发布</li>
 *   <li>基于 CDI 的事件观察者模式</li>
 *   <li>支持响应式流订阅</li>
 *   <li>自动传播上下文</li>
 * </ul>
 */
@ApplicationScoped
public class LocalEventBus implements EventBus {

    private static final Logger LOG = Logger.getLogger(LocalEventBus.class);

    private final jakarta.enterprise.event.Event<LocalEventWrapper<?>> cdiEvent;
    private final MessagingConfig config;

    // 用于响应式流订阅的广播处理器
    private final Map<String, BroadcastProcessor<io.github.faustofan.admin.shared.messaging.core.Event<?>>> topicProcessors =
            new ConcurrentHashMap<>();

    @Inject
    public LocalEventBus(jakarta.enterprise.event.Event<LocalEventWrapper<?>> cdiEvent, MessagingConfig config) {
        this.cdiEvent = cdiEvent;
        this.config = config;
    }

    // ===========================
    // EventBus 接口实现
    // ===========================

    @Override
    public <T> void publish(io.github.faustofan.admin.shared.messaging.core.Event<T> event) {
        if (!isAvailable()) {
            LOG.warnv("Local event bus is disabled, skipping event: {0}", event.getEventId());
            return;
        }

        LOG.debugv("Publishing local event: {0} to topic: {1}", event.getEventId(), event.getTopic());

        LocalEventWrapper<T> wrapper = new LocalEventWrapper<>(event);

        if (config.local().async()) {
            // 异步发布
            cdiEvent.fireAsync(wrapper)
                    .exceptionally(ex -> {
                        LOG.errorv(ex, "Failed to publish async local event: {0}", event.getEventId());
                        return null;
                    });
        } else {
            // 同步发布
            try {
                cdiEvent.fire(wrapper);
            } catch (Exception e) {
                throw MessagingException.sendFailed(event.getTopic(), e);
            }
        }

        // 广播到流订阅者
        broadcastToSubscribers(event);
    }

    @Override
    public <T> void publish(String topic, io.github.faustofan.admin.shared.messaging.core.Event<T> event) {
        // 对于本地事件，topic由事件本身携带，这里可以覆盖
        LOG.debugv("Publishing local event to topic: {0}", topic);
        publish(event);
    }

    @Override
    public <T> CompletionStage<Void> publishAsync(Event<T> event) {
        if (!isAvailable()) {
            LOG.warnv("Local event bus is disabled, skipping event: {0}", event.getEventId());
            return CompletableFuture.completedFuture(null);
        }

        LOG.debugv("Publishing async local event: {0}", event.getEventId());

        LocalEventWrapper<T> wrapper = new LocalEventWrapper<>(event);

        return cdiEvent.fireAsync(wrapper, NotificationOptions.ofExecutor(Infrastructure.getDefaultExecutor()))
                .thenAccept(v -> broadcastToSubscribers(event));
    }

    @Override
    public <T> Uni<Void> publishUni(io.github.faustofan.admin.shared.messaging.core.Event<T> event) {
        return Uni.createFrom().completionStage(() -> publishAsync(event));
    }

    @Override
    public <T> void send(Message<T> message) {
        LOG.debugv("Sending local message: {0} to topic: {1}", message.getMessageId(), message.getTopic());
        // 将消息包装为事件发布
        MessageEvent<T> messageEvent = new MessageEvent<>(message);
        LocalEventWrapper<Message<T>> wrapper = new LocalEventWrapper<>(messageEvent);
        cdiEvent.fire(wrapper);
    }

    @Override
    public <T> CompletionStage<Void> sendAsync(Message<T> message) {
        return Uni.createFrom().voidItem()
                .invoke(() -> send(message))
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .subscribeAsCompletionStage();
    }

    @Override
    public <T> Uni<Void> sendUni(Message<T> message) {
        return Uni.createFrom().voidItem().invoke(() -> send(message));
    }

    @Override
    public <T> void fire(io.github.faustofan.admin.shared.messaging.core.Event<T> event) {
        // Fire and forget，不等待
        publishAsync(event);
    }

    @Override
    public <T> void fire(String topic, io.github.faustofan.admin.shared.messaging.core.Event<T> event) {
        publishAsync(event);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Multi<io.github.faustofan.admin.shared.messaging.core.Event<T>> subscribe(String topic, Class<T> eventType) {
        BroadcastProcessor<io.github.faustofan.admin.shared.messaging.core.Event<?>> processor =
                topicProcessors.computeIfAbsent(topic, k -> BroadcastProcessor.create());

        return processor
                .filter(event -> topic.equals(event.getTopic()))
                .map(event -> (io.github.faustofan.admin.shared.messaging.core.Event<T>) event);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Multi<Message<T>> subscribeMessages(String topic, Class<T> payloadType) {
        BroadcastProcessor<io.github.faustofan.admin.shared.messaging.core.Event<?>> processor =
                topicProcessors.computeIfAbsent(topic, k -> BroadcastProcessor.create());

        return processor
                .filter(event -> topic.equals(event.getTopic()))
                .filter(event -> event instanceof MessageEvent)
                .map(event -> ((MessageEvent<T>) event).getMessage());
    }

    @Override
    public ChannelType getChannelType() {
        return ChannelType.LOCAL;
    }

    @Override
    public boolean isAvailable() {
        return config.enabled() && config.local().enabled();
    }

    // ===========================
    // 内部方法
    // ===========================

    private <T> void broadcastToSubscribers(io.github.faustofan.admin.shared.messaging.core.Event<T> event) {
        String topic = event.getTopic();
        BroadcastProcessor<io.github.faustofan.admin.shared.messaging.core.Event<?>> processor = topicProcessors.get(topic);
        if (processor != null) {
            processor.onNext(event);
        }
    }

    // ===========================
    // 消息事件包装
    // ===========================

    /**
     * 将 Message 包装为 Event
     */
    private static class MessageEvent<T> extends BaseEvent<Message<T>> {
        private final Message<T> message;

        MessageEvent(Message<T> message) {
            super(null, io.github.faustofan.admin.shared.messaging.constants.EventType.CUSTOM,
                    "message", message.getTopic(), message, message.getHeaders());
            this.message = message;
        }

        public Message<T> getMessage() {
            return message;
        }
    }
}
