package io.github.faustofan.admin.shared.async.context;

import io.github.faustofan.admin.shared.common.AppContextHolder;
import org.jboss.logging.Logger;

import java.util.concurrent.Callable;

/**
 * 上下文透传的 Callable 包装器
 * <p>
 * 在任务创建时捕获上下文，在任务执行时恢复上下文，执行完毕后清理上下文。
 *
 * @param <V> 返回值类型
 */
public class ContextPropagatingCallable<V> implements Callable<V> {

    private static final Logger LOG = Logger.getLogger(ContextPropagatingCallable.class);

    private final Callable<V> delegate;
    private final AsyncContext capturedContext;

    /**
     * 创建上下文透传的 Callable
     *
     * @param delegate 原始 Callable
     */
    public ContextPropagatingCallable(Callable<V> delegate) {
        this.delegate = delegate;
        this.capturedContext = AppContextHolder.capture();
    }

    /**
     * 使用指定上下文创建
     *
     * @param delegate 原始 Callable
     * @param context  指定的上下文
     */
    public ContextPropagatingCallable(Callable<V> delegate, AsyncContext context) {
        this.delegate = delegate;
        this.capturedContext = context;
    }

    @Override
    public V call() throws Exception {
        try {
            // 恢复上下文
            AppContextHolder.restore(capturedContext);
            LOG.tracev("Context restored before callable execution");

            // 执行原始任务
            return delegate.call();

        } finally {
            // 清理上下文
            AppContextHolder.clear();
            LOG.tracev("Context cleared after callable execution");
        }
    }

    /**
     * 包装 Callable 以支持上下文透传
     *
     * @param callable 原始 Callable
     * @param <V>      返回值类型
     * @return 包装后的 Callable
     */
    public static <V> Callable<V> wrap(Callable<V> callable) {
        if (callable instanceof ContextPropagatingCallable) {
            return callable;
        }
        return new ContextPropagatingCallable<>(callable);
    }
}
