package io.github.faustofan.admin.shared.messaging.listener;

import io.github.faustofan.admin.shared.messaging.annotation.EventListener;
import io.github.faustofan.admin.shared.messaging.config.MessagingConfig;
import io.github.faustofan.admin.shared.messaging.constants.ChannelType;
import io.github.faustofan.admin.shared.messaging.constants.EventType;
import io.github.faustofan.admin.shared.messaging.core.Event;
import io.github.faustofan.admin.shared.messaging.core.EventBus;
import io.github.faustofan.admin.shared.messaging.facade.MessagingFacade;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 事件监听器注册中心
 * <p>
 * 在应用启动时扫描所有 {@link EventListener} 注解的方法，
 * 并自动注册为事件监听器。
 * <p>
 * 特性：
 * <ul>
 *   <li>自动扫描和注册</li>
 *   <li>支持多通道（LOCAL/PULSAR/STREAM）</li>
 *   <li>支持事件类型过滤</li>
 *   <li>支持优先级排序</li>
 *   <li>支持失败重试</li>
 * </ul>
 */
@ApplicationScoped
public class EventListenerRegistry {

    private static final Logger LOG = Logger.getLogger(EventListenerRegistry.class);

    @Inject
    MessagingFacade messagingFacade;

    @Inject
    MessagingConfig config;

    @Inject
    BeanManager beanManager;

    /**
     * 已注册的监听器
     */
    private final Map<String, List<ListenerInfo>> registeredListeners = new ConcurrentHashMap<>();

    /**
     * 活跃的订阅
     */
    private final Map<String, AtomicBoolean> activeSubscriptions = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        if (!config.enabled()) {
            LOG.info("Messaging is disabled, skipping EventListener registration");
            return;
        }

        LOG.info("Scanning for @EventListener annotations...");
        scanAndRegisterListeners();
        startListening();
    }

    /**
     * 扫描并注册所有监听器
     */
    private void scanAndRegisterListeners() {
        Set<Bean<?>> beans = beanManager.getBeans(Object.class);
        
        for (Bean<?> bean : beans) {
            Class<?> beanClass = bean.getBeanClass();
            if (beanClass == null) {
                continue;
            }

            for (Method method : beanClass.getDeclaredMethods()) {
                EventListener annotation = method.getAnnotation(EventListener.class);
                if (annotation != null) {
                    registerListener(bean, method, annotation);
                }
            }
        }

        LOG.infov("Registered {0} event listeners for {1} topics", 
            registeredListeners.values().stream().mapToInt(List::size).sum(),
            registeredListeners.size());
    }

    /**
     * 注册单个监听器
     */
    private void registerListener(Bean<?> bean, Method method, EventListener annotation) {
        String topic = annotation.topic();
        
        ListenerInfo info = new ListenerInfo(
            bean,
            method,
            annotation.eventType(),
            annotation.channel(),
            annotation.consumerGroup(),
            annotation.async(),
            annotation.retryCount(),
            parseDuration(annotation.retryInterval()),
            annotation.condition(),
            annotation.priority(),
            annotation.description()
        );

        registeredListeners.computeIfAbsent(topic, k -> new ArrayList<>()).add(info);

        // 按优先级排序
        registeredListeners.get(topic).sort(Comparator.comparingInt(l -> l.priority));

        LOG.debugv("Registered listener: {0}.{1} for topic: {2}", 
            bean.getBeanClass().getSimpleName(), method.getName(), topic);
    }

    /**
     * 开始监听所有已注册的 Topic
     */
    private void startListening() {
        for (Map.Entry<String, List<ListenerInfo>> entry : registeredListeners.entrySet()) {
            String topic = entry.getKey();
            List<ListenerInfo> listeners = entry.getValue();

            if (listeners.isEmpty()) {
                continue;
            }

            // 确定使用哪个通道（取第一个监听器的配置或默认）
            ChannelType channelType = listeners.get(0).channel;
            if (channelType == ChannelType.AUTO) {
                channelType = config.channel();
            }

            try {
                EventBus eventBus = messagingFacade.getEventBus(channelType);
                subscribeToTopic(topic, eventBus, listeners);
            } catch (Exception e) {
                LOG.warnv("Failed to subscribe to topic {0}: {1}", topic, e.getMessage());
            }
        }
    }

    /**
     * 订阅 Topic 并分发事件
     */
    @SuppressWarnings("unchecked")
    private void subscribeToTopic(String topic, EventBus eventBus, List<ListenerInfo> listeners) {
        AtomicBoolean active = new AtomicBoolean(true);
        activeSubscriptions.put(topic, active);

        Multi<Event<Object>> eventStream = eventBus.subscribe(topic, Object.class);

        eventStream
            .onItem().invoke(event -> {
                if (!active.get()) {
                    return;
                }

                for (ListenerInfo listener : listeners) {
                    if (shouldProcess(event, listener)) {
                        invokeListener(event, listener);
                    }
                }
            })
            .onFailure().invoke(error -> {
                LOG.errorv("Error in event stream for topic {0}: {1}", topic, error.getMessage());
            })
            .subscribe().with(
                ignored -> {},
                error -> LOG.errorv("Event stream subscription failed for topic {0}: {1}", topic, error.getMessage())
            );

        LOG.infov("Started listening on topic: {0} with {1} listener(s)", topic, listeners.size());
    }

    /**
     * 检查是否应该处理此事件
     */
    private boolean shouldProcess(Event<?> event, ListenerInfo listener) {
        // 检查事件类型过滤
        if (listener.eventTypes.length > 0) {
            boolean typeMatch = false;
            for (EventType type : listener.eventTypes) {
                if (type == event.getEventType()) {
                    typeMatch = true;
                    break;
                }
            }
            if (!typeMatch) {
                return false;
            }
        }

        // TODO: 条件表达式检查（需要 SpEL 解析器）

        return true;
    }

    /**
     * 调用监听器方法
     */
    private void invokeListener(Event<?> event, ListenerInfo listener) {
        Runnable task = () -> {
            try {
                Object beanInstance = beanManager.getReference(
                    listener.bean,
                    listener.bean.getBeanClass(),
                    beanManager.createCreationalContext(listener.bean)
                );

                listener.method.setAccessible(true);
                listener.method.invoke(beanInstance, event);

                LOG.debugv("Event handled by {0}.{1}", 
                    listener.bean.getBeanClass().getSimpleName(), 
                    listener.method.getName());

            } catch (Exception e) {
                handleListenerError(event, listener, e, 0);
            }
        };

        if (listener.async) {
            Infrastructure.getDefaultExecutor().execute(task);
        } else {
            task.run();
        }
    }

    /**
     * 处理监听器错误
     */
    private void handleListenerError(Event<?> event, ListenerInfo listener, Exception e, int attempt) {
        LOG.errorv("Error invoking listener {0}.{1}: {2}", 
            listener.bean.getBeanClass().getSimpleName(),
            listener.method.getName(),
            e.getMessage());

        if (attempt < listener.retryCount) {
            // 重试
            LOG.infov("Retrying listener {0}.{1}, attempt {2}/{3}",
                listener.bean.getBeanClass().getSimpleName(),
                listener.method.getName(),
                attempt + 1,
                listener.retryCount);

            try {
                Thread.sleep(listener.retryInterval.toMillis());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }

            invokeListener(event, listener);
        }
    }

    /**
     * 停止所有订阅
     */
    public void stopAllListeners() {
        for (AtomicBoolean active : activeSubscriptions.values()) {
            active.set(false);
        }
        LOG.info("All event listeners stopped");
    }

    /**
     * 获取已注册的监听器数量
     */
    public int getListenerCount() {
        return registeredListeners.values().stream().mapToInt(List::size).sum();
    }

    /**
     * 获取已注册的 Topic 数量
     */
    public int getTopicCount() {
        return registeredListeners.size();
    }

    private Duration parseDuration(String duration) {
        try {
            return Duration.parse(duration);
        } catch (Exception e) {
            return Duration.ofSeconds(1);
        }
    }

    /**
     * 监听器信息
     */
    private static class ListenerInfo {
        final Bean<?> bean;
        final Method method;
        final EventType[] eventTypes;
        final ChannelType channel;
        final String consumerGroup;
        final boolean async;
        final int retryCount;
        final Duration retryInterval;
        final String condition;
        final int priority;
        final String description;

        ListenerInfo(Bean<?> bean, Method method, EventType[] eventTypes, ChannelType channel,
                     String consumerGroup, boolean async, int retryCount, Duration retryInterval,
                     String condition, int priority, String description) {
            this.bean = bean;
            this.method = method;
            this.eventTypes = eventTypes;
            this.channel = channel;
            this.consumerGroup = consumerGroup;
            this.async = async;
            this.retryCount = retryCount;
            this.retryInterval = retryInterval;
            this.condition = condition;
            this.priority = priority;
            this.description = description;
        }
    }
}
