package io.github.faustofan.admin.shared.messaging.stream;

import io.github.faustofan.admin.shared.messaging.core.Event;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 事件流构建器
 * <p>
 * 提供流畅的 API 来构建事件处理管道
 *
 * @param <T> 事件负载类型
 */
public class EventStreamBuilder<T> {

    private Multi<Event<T>> source;

    private EventStreamBuilder(Multi<Event<T>> source) {
        this.source = source;
    }

    /**
     * 从 Multi 创建构建器
     */
    public static <T> EventStreamBuilder<T> from(Multi<Event<T>> source) {
        return new EventStreamBuilder<>(source);
    }

    /**
     * 过滤事件
     */
    public EventStreamBuilder<T> filter(Predicate<Event<T>> predicate) {
        this.source = source.filter(predicate);
        return this;
    }

    /**
     * 按事件类型过滤
     */
    public EventStreamBuilder<T> filterByType(io.github.faustofan.admin.shared.messaging.constants.EventType eventType) {
        return filter(event -> event.getEventType() == eventType);
    }

    /**
     * 按租户过滤
     */
    public EventStreamBuilder<T> filterByTenant(Long tenantId) {
        return filter(event -> tenantId.equals(event.getTenantId()));
    }

    /**
     * 转换事件
     */
    public <R> EventStreamBuilder<R> map(Function<Event<T>, Event<R>> mapper) {
        Multi<Event<R>> mapped = source.map(mapper);
        return new EventStreamBuilder<>(mapped);
    }

    /**
     * 转换负载
     */
    public <R> Multi<R> mapPayload(Function<T, R> mapper) {
        return source.map(event -> mapper.apply(event.getPayload()));
    }

    /**
     * 异步转换
     */
    public <R> EventStreamBuilder<R> flatMap(Function<Event<T>, Uni<Event<R>>> mapper) {
        Multi<Event<R>> mapped = source.onItem().transformToUni(e -> mapper.apply(e)).merge();
        return new EventStreamBuilder<>(mapped);
    }

    /**
     * 批量处理
     */
    public Multi<List<Event<T>>> batch(int size) {
        return source.group().intoLists().of(size);
    }

    /**
     * 批量处理（带超时）
     */
    public Multi<List<Event<T>>> batch(int size, Duration timeout) {
        return source.group().intoLists().of(size, timeout);
    }

    /**
     * 限流
     */
    public EventStreamBuilder<T> throttle(Duration interval) {
        this.source = source.onItem().call(item ->
                Uni.createFrom().voidItem().onItem().delayIt().by(interval));
        return this;
    }

    /**
     * 去重（基于事件ID）
     */
    public EventStreamBuilder<T> distinct() {
        this.source = source.select().distinct(Event::getEventId);
        return this;
    }

    /**
     * 缓冲
     */
    public EventStreamBuilder<T> buffer(int size) {
        this.source = source.onOverflow().buffer(size);
        return this;
    }

    /**
     * 丢弃溢出的事件
     */
    public EventStreamBuilder<T> dropOnOverflow() {
        this.source = source.onOverflow().drop();
        return this;
    }

    /**
     * 错误恢复
     */
    public EventStreamBuilder<T> onFailure(Function<Throwable, Event<T>> recovery) {
        this.source = source.onFailure().recoverWithItem(recovery);
        return this;
    }

    /**
     * 重试
     */
    public EventStreamBuilder<T> retry(int times) {
        this.source = source.onFailure().retry().atMost(times);
        return this;
    }

    /**
     * 获取构建的流
     */
    public Multi<Event<T>> build() {
        return source;
    }

    /**
     * 订阅处理
     */
    public void subscribe(java.util.function.Consumer<Event<T>> onItem,
                          java.util.function.Consumer<Throwable> onError) {
        source.subscribe().with(onItem, onError);
    }

    /**
     * 订阅处理（包含完成回调）
     */
    public void subscribe(java.util.function.Consumer<Event<T>> onItem,
                          java.util.function.Consumer<Throwable> onError,
                          Runnable onComplete) {
        source.subscribe().with(onItem, onError, onComplete);
    }
}
