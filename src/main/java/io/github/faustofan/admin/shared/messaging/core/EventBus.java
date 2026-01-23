package io.github.faustofan.admin.shared.messaging.core;

import io.github.faustofan.admin.shared.messaging.constants.ChannelType;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import java.util.concurrent.CompletionStage;

/**
 * 事件总线接口
 * <p>
 * 定义事件发布和订阅的统一契约，所有通道实现都应遵循此接口
 */
public interface EventBus {

    /**
     * 发布事件（同步，阻塞等待确认）
     *
     * @param event 事件
     * @param <T>   负载类型
     */
    <T> void publish(Event<T> event);

    /**
     * 发布事件到指定Topic（同步）
     *
     * @param topic 目标Topic
     * @param event 事件
     * @param <T>   负载类型
     */
    <T> void publish(String topic, Event<T> event);

    /**
     * 发布事件（异步）
     *
     * @param event 事件
     * @param <T>   负载类型
     * @return CompletionStage 表示发送完成
     */
    <T> CompletionStage<Void> publishAsync(Event<T> event);

    /**
     * 发布事件（Mutiny Uni）
     *
     * @param event 事件
     * @param <T>   负载类型
     * @return Uni 表示发送完成
     */
    <T> Uni<Void> publishUni(Event<T> event);

    /**
     * 发布消息（同步）
     *
     * @param message 消息
     * @param <T>     负载类型
     */
    <T> void send(Message<T> message);

    /**
     * 发布消息（异步）
     *
     * @param message 消息
     * @param <T>     负载类型
     * @return CompletionStage 表示发送完成
     */
    <T> CompletionStage<Void> sendAsync(Message<T> message);

    /**
     * 发布消息（Mutiny Uni）
     *
     * @param message 消息
     * @param <T>     负载类型
     * @return Uni 表示发送完成
     */
    <T> Uni<Void> sendUni(Message<T> message);

    /**
     * 即发即忘（Fire and Forget）
     * <p>
     * 不等待确认，立即返回
     *
     * @param event 事件
     * @param <T>   负载类型
     */
    <T> void fire(Event<T> event);

    /**
     * 即发即忘到指定Topic
     *
     * @param topic 目标Topic
     * @param event 事件
     * @param <T>   负载类型
     */
    <T> void fire(String topic, Event<T> event);

    /**
     * 获取事件流（用于响应式订阅）
     *
     * @param topic     订阅的Topic
     * @param eventType 事件类型
     * @param <T>       负载类型
     * @return Multi 事件流
     */
    <T> Multi<Event<T>> subscribe(String topic, Class<T> eventType);

    /**
     * 获取消息流
     *
     * @param topic       订阅的Topic
     * @param payloadType 负载类型
     * @param <T>         负载类型
     * @return Multi 消息流
     */
    <T> Multi<Message<T>> subscribeMessages(String topic, Class<T> payloadType);

    /**
     * 获取通道类型
     */
    ChannelType getChannelType();

    /**
     * 检查是否可用
     */
    boolean isAvailable();
}
