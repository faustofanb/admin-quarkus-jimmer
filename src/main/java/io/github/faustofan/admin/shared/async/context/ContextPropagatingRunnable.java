package io.github.faustofan.admin.shared.async.context;

import io.github.faustofan.admin.shared.common.AppContextHolder;
import org.jboss.logging.Logger;

/**
 * 上下文透传的 Runnable 包装器
 * <p>
 * 在任务创建时捕获上下文，在任务执行时恢复上下文，执行完毕后清理上下文。
 */
public class ContextPropagatingRunnable implements Runnable {

    private static final Logger LOG = Logger.getLogger(ContextPropagatingRunnable.class);

    private final Runnable delegate;
    private final AsyncContext capturedContext;

    /**
     * 创建上下文透传的 Runnable
     *
     * @param delegate 原始 Runnable
     */
    public ContextPropagatingRunnable(Runnable delegate) {
        this.delegate = delegate;
        this.capturedContext = AppContextHolder.capture();
    }

    /**
     * 使用指定上下文创建
     *
     * @param delegate 原始 Runnable
     * @param context  指定的上下文
     */
    public ContextPropagatingRunnable(Runnable delegate, AsyncContext context) {
        this.delegate = delegate;
        this.capturedContext = context;
    }

    @Override
    public void run() {
        try {
            // 恢复上下文
            AppContextHolder.restore(capturedContext);
            LOG.tracev("Context restored before task execution");

            // 执行原始任务
            delegate.run();

        } finally {
            // 清理上下文
            AppContextHolder.clear();
            LOG.tracev("Context cleared after task execution");
        }
    }

    /**
     * 包装 Runnable 以支持上下文透传
     *
     * @param runnable 原始 Runnable
     * @return 包装后的 Runnable
     */
    public static Runnable wrap(Runnable runnable) {
        if (runnable instanceof ContextPropagatingRunnable) {
            return runnable;
        }
        return new ContextPropagatingRunnable(runnable);
    }
}
