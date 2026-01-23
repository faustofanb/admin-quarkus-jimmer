package io.github.faustofan.admin.shared.messaging.facade;

import io.github.faustofan.admin.shared.messaging.constants.ChannelType;
import io.github.faustofan.admin.shared.messaging.core.Event;
import io.github.faustofan.admin.shared.messaging.core.Message;
import io.github.faustofan.admin.shared.messaging.core.EventBus;
import io.github.faustofan.admin.shared.messaging.config.MessagingConfig;
import io.github.faustofan.admin.shared.messaging.exception.MessagingException;
import io.github.faustofan.admin.shared.messaging.local.LocalEventBus;
import io.github.faustofan.admin.shared.messaging.pulsar.PulsarEventBus;
import io.github.faustofan.admin.shared.messaging.stream.StreamEventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * MessagingFacade
 * <p>
 * 统一的消息总线门面，负责根据 {@link MessagingConfig} 中的配置选择合适的 {@link EventBus}
 * 实现对外统一的发布、发送、广播等操作。
 * <p>
 * 设计目标：
 * <ul>
 *   <li>对外只暴露一套 API，隐藏底层实现细节（Local / Pulsar / Stream）</li>
 *   <li>支持通过配置切换默认通道，亦可显式指定通道</li>
 *   <li>在通道不可用时抛出统一的 {@link MessagingException}</li>
 * </ul>
 */
@ApplicationScoped
public class MessagingFacade {

    private static final Logger LOG = Logger.getLogger(MessagingFacade.class);

    private final MessagingConfig config;
    private final EventBus localEventBus;
    private final EventBus pulsarEventBus;
    private final EventBus streamEventBus;

    @Inject
    public MessagingFacade(MessagingConfig config,
                           // CDI will inject the concrete implementations
                           LocalEventBus localEventBus,
                           PulsarEventBus pulsarEventBus,
                           StreamEventBus streamEventBus) {
        this.config = config;
        this.localEventBus = localEventBus;
        this.pulsarEventBus = pulsarEventBus;
        this.streamEventBus = streamEventBus;
        LOG.info("MessagingFacade initialized – default channel: " + config.channel());
    }

    /**
     * 根据 {@link ChannelType} 获取对应的 {@link EventBus} 实例。
     * 如果对应通道未启用，将抛出 {@link MessagingException}。
     */
    public EventBus getEventBus(ChannelType channel) {
        switch (channel) {
            case LOCAL:
                if (localEventBus.isAvailable()) {
                    return localEventBus;
                }
                break;
            case PULSAR:
                if (pulsarEventBus.isAvailable()) {
                    return pulsarEventBus;
                }
                break;
            case STREAM:
                if (streamEventBus.isAvailable()) {
                    return streamEventBus;
                }
                break;
            default:
                // 其它未来可能的实现
                break;
        }
        // 若走到这里说明通道不可用
        throw MessagingException.channelUnavailable(
                channel,
                "Channel " + channel + " is disabled or not configured properly");
    }

    /**
     * 获取默认的 EventBus（由配置 {@code admin.messaging.default-channel} 决定）。
     */
    public EventBus getDefaultEventBus() {
        return getEventBus(config.channel());
    }

    // ---------------------------------------------------------------------
    // 统一的发布 / 发送 API（使用默认通道）
    // ---------------------------------------------------------------------
    public <T> void publish(Event<T> event) {
        getDefaultEventBus().publish(event);
    }

    public <T> void publish(String topic, Event<T> event) {
        getDefaultEventBus().publish(topic, event);
    }

    public <T> void send(Message<T> message) {
        getDefaultEventBus().send(message);
    }

    public <T> void fire(Event<T> event) {
        getDefaultEventBus().fire(event);
    }

    public <T> void fire(String topic, Event<T> event) {
        getDefaultEventBus().fire(topic, event);
    }

    // ---------------------------------------------------------------------
    // 异步/响应式 API（使用默认通道）
    // ---------------------------------------------------------------------
    public <T> java.util.concurrent.CompletionStage<Void> publishAsync(Event<T> event) {
        return getDefaultEventBus().publishAsync(event);
    }

    public <T> io.smallrye.mutiny.Uni<Void> publishUni(Event<T> event) {
        return getDefaultEventBus().publishUni(event);
    }

    public <T> java.util.concurrent.CompletionStage<Void> sendAsync(Message<T> message) {
        return getDefaultEventBus().sendAsync(message);
    }

    public <T> io.smallrye.mutiny.Uni<Void> sendUni(Message<T> message) {
        return getDefaultEventBus().sendUni(message);
    }

    // ---------------------------------------------------------------------
    // 订阅 API（使用默认通道）
    // ---------------------------------------------------------------------
    public <T> io.smallrye.mutiny.Multi<Event<T>> subscribe(String topic, Class<T> payloadType) {
        return getDefaultEventBus().subscribe(topic, payloadType);
    }

    public <T> io.smallrye.mutiny.Multi<Message<T>> subscribeMessages(String topic, Class<T> payloadType) {
        return getDefaultEventBus().subscribeMessages(topic, payloadType);
    }
}
