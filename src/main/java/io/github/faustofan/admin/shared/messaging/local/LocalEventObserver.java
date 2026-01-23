package io.github.faustofan.admin.shared.messaging.local;

import io.github.faustofan.admin.shared.messaging.core.Event;
import io.github.faustofan.admin.shared.messaging.core.EventHandler;
import jakarta.enterprise.event.ObservesAsync;
import org.jboss.logging.Logger;

/**
 * 本地事件观察者基类
 * <p>
 * 提供便捷的本地事件监听能力，业务代码可以继承此类实现具体的事件处理逻辑。
 * <p>
 * 使用示例：
 * <pre>{@code
 * @ApplicationScoped
 * public class UserCreatedObserver extends LocalEventObserver<UserPayload> {
 *
 *     @Override
 *     protected String getTargetTopic() {
 *         return MessagingConstants.SystemTopic.USER_EVENTS;
 *     }
 *
 *     @Override
 *     public void handle(Event<UserPayload> event) {
 *         log.info("User created: " + event.getPayload().getUsername());
 *     }
 * }
 * }</pre>
 *
 * @param <T> 事件负载类型
 */
public abstract class LocalEventObserver<T> implements EventHandler<T> {

    private static final Logger LOG = Logger.getLogger(LocalEventObserver.class);

    /**
     * CDI 异步事件观察者方法
     * <p>
     * 接收所有本地事件，通过 Topic 过滤
     */
    @SuppressWarnings("unchecked")
    public void onEvent(@ObservesAsync LocalEventWrapper<?> wrapper) {
        // Topic 过滤
        String targetTopic = getTargetTopic();
        if (targetTopic != null && !wrapper.matchesTopic(targetTopic)) {
            return;
        }

        // Topic 前缀过滤
        String targetPrefix = getTargetTopicPrefix();
        if (targetPrefix != null && !wrapper.matchesTopicPrefix(targetPrefix)) {
            return;
        }

        try {
            Event<T> event = (Event<T>) wrapper.getEvent();
            LOG.debugv("Handling local event: {0} in observer: {1}",
                    event.getEventId(), getHandlerName());

            handle(event);

            LOG.debugv("Successfully handled local event: {0}", event.getEventId());
        } catch (ClassCastException e) {
            LOG.debugv("Event payload type mismatch, skipping: {0}", wrapper.getEventId());
        } catch (Exception e) {
            LOG.errorv(e, "Failed to handle local event: {0}", wrapper.getEventId());
            onError(wrapper, e);
        }
    }

    /**
     * 获取目标 Topic（精确匹配）
     * <p>
     * 返回 null 表示不按 Topic 过滤
     */
    protected String getTargetTopic() {
        return getTopic();
    }

    /**
     * 获取目标 Topic 前缀（前缀匹配）
     * <p>
     * 返回 null 表示不按前缀过滤
     */
    protected String getTargetTopicPrefix() {
        return null;
    }

    /**
     * 处理错误回调
     * <p>
     * 子类可以覆盖此方法实现自定义错误处理
     */
    protected void onError(LocalEventWrapper<?> wrapper, Exception e) {
        // 默认不做额外处理，由异步事件框架处理
    }

    @Override
    public String getHandlerName() {
        return this.getClass().getSimpleName();
    }
}
