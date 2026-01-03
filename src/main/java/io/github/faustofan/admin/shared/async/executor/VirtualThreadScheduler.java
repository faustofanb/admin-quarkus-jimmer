package io.github.faustofan.admin.shared.async.executor;

import io.github.faustofan.admin.shared.async.constants.AsyncConstants;
import io.github.faustofan.admin.shared.async.context.AsyncContext;
import io.github.faustofan.admin.shared.common.AppContextHolder;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.concurrent.*;

/**
 * 虚拟线程调度器
 * <p>
 * 提供延迟执行和定时任务调度功能
 */
public final class VirtualThreadScheduler {

    private static final Logger LOG = Logger.getLogger(VirtualThreadScheduler.class);

    /**
     * 定时任务调度器
     */
    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(
            1,
            Thread.ofVirtual()
                    .name(AsyncConstants.VIRTUAL_THREAD_NAME_PREFIX + "scheduler-", 0)
                    .factory()
    );

    private VirtualThreadScheduler() {
        // 禁止实例化
    }

    // ===========================
    // 延迟执行
    // ===========================

    /**
     * 延迟执行任务
     *
     * @param task  任务
     * @param delay 延迟时间
     * @return ScheduledFuture
     */
    public static ScheduledFuture<?> schedule(Runnable task, Duration delay) {
        AsyncContext context = AppContextHolder.capture();
        return SCHEDULER.schedule(() -> executeWithContext(task, context), delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * 延迟执行有返回值的任务
     *
     * @param task  任务
     * @param delay 延迟时间
     * @param <V>   返回值类型
     * @return ScheduledFuture
     */
    public static <V> ScheduledFuture<V> schedule(Callable<V> task, Duration delay) {
        AsyncContext context = AppContextHolder.capture();
        return SCHEDULER.schedule(() -> executeWithContext(task, context), delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    // ===========================
    // 周期性执行
    // ===========================

    /**
     * 固定速率执行（从每次执行开始时计算下一次执行时间）
     *
     * @param task         任务
     * @param initialDelay 初始延迟
     * @param period       执行周期
     * @return ScheduledFuture
     */
    public static ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration initialDelay, Duration period) {
        // 周期性任务不透传上下文，每次执行时上下文可能已变化
        return SCHEDULER.scheduleAtFixedRate(
                () -> {
                    try {
                        task.run();
                    } catch (Exception e) {
                        LOG.errorv(e, "Scheduled task execution failed");
                    }
                },
                initialDelay.toMillis(),
                period.toMillis(),
                TimeUnit.MILLISECONDS
        );
    }

    /**
     * 固定延迟执行（从每次执行结束时计算下一次执行时间）
     *
     * @param task         任务
     * @param initialDelay 初始延迟
     * @param delay        执行间隔
     * @return ScheduledFuture
     */
    public static ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration initialDelay, Duration delay) {
        return SCHEDULER.scheduleWithFixedDelay(
                () -> {
                    try {
                        task.run();
                    } catch (Exception e) {
                        LOG.errorv(e, "Scheduled task execution failed");
                    }
                },
                initialDelay.toMillis(),
                delay.toMillis(),
                TimeUnit.MILLISECONDS
        );
    }

    // ===========================
    // 内部辅助方法
    // ===========================

    private static void executeWithContext(Runnable task, AsyncContext context) {
        try {
            AppContextHolder.restore(context);
            task.run();
        } finally {
            AppContextHolder.clear();
        }
    }

    private static <V> V executeWithContext(Callable<V> task, AsyncContext context) throws Exception {
        try {
            AppContextHolder.restore(context);
            return task.call();
        } finally {
            AppContextHolder.clear();
        }
    }

    // ===========================
    // 生命周期管理
    // ===========================

    /**
     * 关闭调度器
     */
    public static void shutdown() {
        LOG.info("Shutting down virtual thread scheduler");
        SCHEDULER.shutdown();
        try {
            if (!SCHEDULER.awaitTermination(10, TimeUnit.SECONDS)) {
                LOG.warn("Scheduler did not terminate in time, forcing shutdown");
                SCHEDULER.shutdownNow();
            }
        } catch (InterruptedException e) {
            LOG.warn("Interrupted while waiting for scheduler shutdown");
            SCHEDULER.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
