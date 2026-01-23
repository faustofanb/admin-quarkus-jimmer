package io.github.faustofan.admin.shared.messaging.stream;

import io.github.faustofan.admin.shared.messaging.config.MessagingConfig;
import io.github.faustofan.admin.shared.messaging.constants.ChannelType;
import io.github.faustofan.admin.shared.messaging.core.*;
import io.github.faustofan.admin.shared.messaging.exception.MessagingException;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import io.smallrye.mutiny.operators.multi.processors.UnicastProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 响应式流事件总线
 * <p>
 * 提供响应式流（Reactive Streams）方式的事件处理能力。
 * <p>
 * 特性：
 * <ul>
 *   <li>基于 Mutiny Multi/Uni 的响应式API</li>
 *   <li>支持背压（Backpressure）</li>
 *   <li>支持流转换、过滤、合并等操作</li>
 *   <li>支持广播（一对多）和单播（一对一）模式</li>
 *   <li>流式批量处理</li>
 * </ul>
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 订阅事件流
 * streamEventBus.subscribe("admin.system.user", UserPayload.class)
 *     .filter(event -> event.getEventType() == EventType.CREATED)
 *     .onItem().transformToUni(event -> processUserCreated(event))
 *     .subscribe().with(
 *         result -> log.info("Processed: " + result),
 *         error -> log.error("Error", error)
 *     );
 *
 * // 发布事件到流
 * streamEventBus.publishUni(event)
 *     .subscribe().with(v -> log.info("Published"));
 * }</pre>
 */
@ApplicationScoped
public class StreamEventBus implements EventBus {

    private static final Logger LOG = Logger.getLogger(StreamEventBus.class);

    private final MessagingConfig config;

    // 广播处理器 - 一对多
    private final Map<String, BroadcastProcessor<Event<?>>> broadcastProcessors = new ConcurrentHashMap<>();

    // 单播处理器 - 一对一
    private final Map<String, UnicastProcessor<Event<?>>> unicastProcessors = new ConcurrentHashMap<>();

    // 消息处理器
    private final Map<String, BroadcastProcessor<Message<?>>> messageProcessors = new ConcurrentHashMap<>();

    @Inject
    public StreamEventBus(MessagingConfig config) {
        this.config = config;
        LOG.info("Stream event bus initialized with buffer size: " + config.stream().bufferSize());
    }

    // ===========================
    // EventBus 接口实现
    // ===========================

    @Override
    public <T> void publish(Event<T> event) {
        if (!isAvailable()) {
            LOG.warnv("Stream event bus is disabled, skipping event: {0}", event.getEventId());
            return;
        }

        String topic = event.getTopic();
        LOG.debugv("Publishing to stream: {0} -> {1}", event.getEventId(), topic);

        // 广播到所有订阅者
        BroadcastProcessor<Event<?>> broadcastProcessor = broadcastProcessors.get(topic);
        if (broadcastProcessor != null) {
            broadcastProcessor.onNext(event);
        }

        // 发送到单播订阅者
        UnicastProcessor<Event<?>> unicastProcessor = unicastProcessors.get(topic);
        if (unicastProcessor != null) {
            unicastProcessor.onNext(event);
        }
    }

    @Override
    public <T> void publish(String topic, Event<T> event) {
        LOG.debugv("Publishing to stream topic: {0}", topic);
        
        BroadcastProcessor<Event<?>> processor = broadcastProcessors.get(topic);
        if (processor != null) {
            processor.onNext(event);
        }
    }

    @Override
    public <T> CompletionStage<Void> publishAsync(Event<T> event) {
        return publishUni(event).subscribeAsCompletionStage();
    }

    @Override
    public <T> Uni<Void> publishUni(Event<T> event) {
        return Uni.createFrom().voidItem()
                .invoke(() -> publish(event));
    }

    @Override
    public <T> void send(Message<T> message) {
        if (!isAvailable()) {
            LOG.warnv("Stream event bus is disabled, skipping message: {0}", message.getMessageId());
            return;
        }

        String topic = message.getTopic();
        LOG.debugv("Sending message to stream: {0} -> {1}", message.getMessageId(), topic);

        BroadcastProcessor<Message<?>> processor = messageProcessors.get(topic);
        if (processor != null) {
            processor.onNext(message);
        }
    }

    @Override
    public <T> CompletionStage<Void> sendAsync(Message<T> message) {
        return sendUni(message).subscribeAsCompletionStage();
    }

    @Override
    public <T> Uni<Void> sendUni(Message<T> message) {
        return Uni.createFrom().voidItem()
                .invoke(() -> send(message));
    }

    @Override
    public <T> void fire(Event<T> event) {
        publish(event);
    }

    @Override
    public <T> void fire(String topic, Event<T> event) {
        publish(topic, event);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Multi<Event<T>> subscribe(String topic, Class<T> eventType) {
        BroadcastProcessor<Event<?>> processor = broadcastProcessors.computeIfAbsent(
                topic, k -> BroadcastProcessor.create());

        return processor
                .filter(event -> topic.equals(event.getTopic()))
                .map(event -> (Event<T>) event);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Multi<Message<T>> subscribeMessages(String topic, Class<T> payloadType) {
        BroadcastProcessor<Message<?>> processor = messageProcessors.computeIfAbsent(
                topic, k -> BroadcastProcessor.create());

        return processor
                .filter(msg -> topic.equals(msg.getTopic()))
                .map(msg -> (Message<T>) msg);
    }

    @Override
    public ChannelType getChannelType() {
        return ChannelType.STREAM;
    }

    @Override
    public boolean isAvailable() {
        return config.enabled() && config.stream().enabled();
    }

    // ===========================
    // 扩展流操作 API
    // ===========================

    /**
     * 订阅事件（单播模式 - 只有一个消费者接收消息）
     */
    @SuppressWarnings("unchecked")
    public <T> Multi<Event<T>> subscribeUnicast(String topic, Class<T> eventType) {
        UnicastProcessor<Event<?>> processor = unicastProcessors.computeIfAbsent(
                topic, k -> UnicastProcessor.create());

        return processor
                .filter(event -> topic.equals(event.getTopic()))
                .map(event -> (Event<T>) event);
    }

    /**
     * 创建事件流管道
     *
     * @param topic   订阅的Topic
     * @param handler 流处理管道
     * @param <T>     负载类型
     * @param <R>     输出类型
     * @return 处理后的结果流
     */
    public <T, R> Multi<R> pipe(String topic, Class<T> eventType,
                                 Function<Multi<Event<T>>, Multi<R>> handler) {
        Multi<Event<T>> source = subscribe(topic, eventType);
        return handler.apply(source);
    }

    /**
     * 批量处理事件
     *
     * @param topic        订阅的Topic
     * @param batchSize    批量大小
     * @param batchTimeout 批量超时
     * @param <T>          负载类型
     * @return 批量事件流
     */
    public <T> Multi<java.util.List<Event<T>>> subscribeBatch(String topic, Class<T> eventType,
                                                               int batchSize, Duration batchTimeout) {
        return subscribe(topic, eventType)
                .group().intoLists().of(batchSize, batchTimeout);
    }

    /**
     * 创建事件发射器
     * <p>
     * 返回一个 Consumer，可以用于手动发射事件到流
     */
    public <T> Consumer<Event<T>> createEmitter(String topic) {
        BroadcastProcessor<Event<?>> processor = broadcastProcessors.computeIfAbsent(
                topic, k -> BroadcastProcessor.create());

        return event -> processor.onNext(event);
    }

    /**
     * 合并多个Topic的事件流
     */
    @SafeVarargs
    public final <T> Multi<Event<T>> merge(String... topics) {
        if (topics == null || topics.length == 0) {
            return Multi.createFrom().empty();
        }

        @SuppressWarnings("unchecked")
        Multi<Event<T>>[] streams = new Multi[topics.length];
        for (int i = 0; i < topics.length; i++) {
            streams[i] = subscribe(topics[i], null);
        }

        return Multi.createBy().merging().streams(streams);
    }

    /**
     * 关闭指定Topic的流
     */
    public void closeStream(String topic) {
        BroadcastProcessor<Event<?>> broadcast = broadcastProcessors.remove(topic);
        if (broadcast != null) {
            broadcast.onComplete();
        }

        UnicastProcessor<Event<?>> unicast = unicastProcessors.remove(topic);
        if (unicast != null) {
            unicast.onComplete();
        }

        BroadcastProcessor<Message<?>> message = messageProcessors.remove(topic);
        if (message != null) {
            message.onComplete();
        }

        LOG.debugv("Closed stream for topic: {0}", topic);
    }

    /**
     * 关闭所有流
     */
    public void closeAllStreams() {
        broadcastProcessors.values().forEach(BroadcastProcessor::onComplete);
        broadcastProcessors.clear();

        unicastProcessors.values().forEach(UnicastProcessor::onComplete);
        unicastProcessors.clear();

        messageProcessors.values().forEach(BroadcastProcessor::onComplete);
        messageProcessors.clear();

        LOG.info("All event streams closed");
    }
}
