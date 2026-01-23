package io.github.faustofan.admin.shared.messaging.core;

import io.smallrye.mutiny.Uni;

import java.util.concurrent.CompletionStage;

/**
 * 事件处理器接口
 * <p>
 * 定义事件处理的契约，用于接收和处理事件
 *
 * @param <T> 事件负载类型
 */
@FunctionalInterface
public interface EventHandler<T> {

    /**
     * 处理事件
     *
     * @param event 收到的事件
     */
    void handle(Event<T> event);

    /**
     * 获取处理器名称
     */
    default String getHandlerName() {
        return this.getClass().getSimpleName();
    }

    /**
     * 订阅的Topic
     */
    default String getTopic() {
        return null; // 如果返回null，则不自动订阅
    }

    /**
     * 处理顺序（数值越小优先级越高）
     */
    default int getOrder() {
        return 0;
    }

    /**
     * 处理事件（异步）
     */
    default java.util.concurrent.CompletionStage<Void> handleAsync(Event<T> event) {
        return java.util.concurrent.CompletableFuture.completedFuture(null).thenRun(() -> handle(event));
    }

    /**
     * 处理事件（Mutiny Uni）
     */
    default Uni<Void> handleUni(Event<T> event) {
        return Uni.createFrom().voidItem().invoke(() -> handle(event));
    }
}
