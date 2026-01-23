package io.github.faustofan.admin.shared.async.executor;

import io.github.faustofan.admin.shared.async.constants.AsyncConstants;
import io.github.faustofan.admin.shared.async.context.AsyncContext;
import io.github.faustofan.admin.shared.common.AppContextHolder;
import io.github.faustofan.admin.shared.async.context.ContextPropagatingCallable;
import io.github.faustofan.admin.shared.async.context.ContextPropagatingRunnable;
import org.jboss.logging.Logger;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 虚拟线程执行器工厂
 * <p>
 * 基于 JDK21 虚拟线程，自动支持上下文透传。
 * 提供多种预配置的执行器。
 */
public final class VirtualThreadExecutorFactory {

    private static final Logger LOG = Logger.getLogger(VirtualThreadExecutorFactory.class);

    /**
     * 线程编号计数器
     */
    private static final AtomicLong THREAD_COUNTER = new AtomicLong(0);

    /**
     * 默认虚拟线程执行器（带上下文透传）
     */
    private static final ExecutorService DEFAULT_EXECUTOR = createContextAwareExecutor(
            AsyncConstants.VIRTUAL_THREAD_NAME_PREFIX + "default-"
    );

    private VirtualThreadExecutorFactory() {
        // 禁止实例化
    }

    // ===========================
    // 执行器创建
    // ===========================

    /**
     * 获取默认虚拟线程执行器（带上下文透传）
     */
    public static ExecutorService getDefault() {
        return DEFAULT_EXECUTOR;
    }

    /**
     * 创建支持上下文透传的虚拟线程执行器
     *
     * @param namePrefix 线程名称前缀
     * @return ExecutorService
     */
    public static ExecutorService createContextAwareExecutor(String namePrefix) {
        ThreadFactory factory = createVirtualThreadFactory(namePrefix);
        return new ContextAwareExecutorService(Executors.newThreadPerTaskExecutor(factory));
    }

    /**
     * 创建原始虚拟线程执行器（不带上下文透传）
     *
     * @param namePrefix 线程名称前缀
     * @return ExecutorService
     */
    public static ExecutorService createRawExecutor(String namePrefix) {
        ThreadFactory factory = createVirtualThreadFactory(namePrefix);
        return Executors.newThreadPerTaskExecutor(factory);
    }

    /**
     * 创建虚拟线程工厂
     *
     * @param namePrefix 线程名称前缀
     * @return ThreadFactory
     */
    public static ThreadFactory createVirtualThreadFactory(String namePrefix) {
        return Thread.ofVirtual()
                .name(namePrefix, THREAD_COUNTER.getAndIncrement())
                .factory();
    }

    // ===========================
    // 上下文感知执行器包装
    // ===========================

    /**
     * 上下文感知的执行器包装类
     * <p>
     * 自动为提交的任务包装上下文透传逻辑
     */
    private static class ContextAwareExecutorService implements ExecutorService {

        private final ExecutorService delegate;

        ContextAwareExecutorService(ExecutorService delegate) {
            this.delegate = delegate;
        }

        @Override
        public void execute(Runnable command) {
            delegate.execute(ContextPropagatingRunnable.wrap(command));
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            return delegate.submit(ContextPropagatingCallable.wrap(task));
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            return delegate.submit(ContextPropagatingRunnable.wrap(task), result);
        }

        @Override
        public Future<?> submit(Runnable task) {
            return delegate.submit(ContextPropagatingRunnable.wrap(task));
        }

        @Override
        public <T> java.util.List<Future<T>> invokeAll(java.util.Collection<? extends Callable<T>> tasks)
                throws InterruptedException {
            return delegate.invokeAll(wrapCallables(tasks));
        }

        @Override
        public <T> java.util.List<Future<T>> invokeAll(java.util.Collection<? extends Callable<T>> tasks,
                                                        long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.invokeAll(wrapCallables(tasks), timeout, unit);
        }

        @Override
        public <T> T invokeAny(java.util.Collection<? extends Callable<T>> tasks)
                throws InterruptedException, ExecutionException {
            return delegate.invokeAny(wrapCallables(tasks));
        }

        @Override
        public <T> T invokeAny(java.util.Collection<? extends Callable<T>> tasks,
                               long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return delegate.invokeAny(wrapCallables(tasks), timeout, unit);
        }

        @Override
        public void shutdown() {
            delegate.shutdown();
        }

        @Override
        public java.util.List<Runnable> shutdownNow() {
            return delegate.shutdownNow();
        }

        @Override
        public boolean isShutdown() {
            return delegate.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return delegate.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }

        private <T> java.util.Collection<Callable<T>> wrapCallables(java.util.Collection<? extends Callable<T>> tasks) {
            AsyncContext context = AppContextHolder.capture();
            return tasks.stream()
                    .map(task -> (Callable<T>) new ContextPropagatingCallable<>(task, context))
                    .toList();
        }
    }

    // ===========================
    // 生命周期管理
    // ===========================

    /**
     * 关闭默认执行器
     * <p>
     * 应在应用关闭时调用
     */
    public static void shutdownDefault() {
        LOG.info("Shutting down default virtual thread executor");
        DEFAULT_EXECUTOR.shutdown();
        try {
            if (!DEFAULT_EXECUTOR.awaitTermination(10, TimeUnit.SECONDS)) {
                LOG.warn("Default executor did not terminate in time, forcing shutdown");
                DEFAULT_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            LOG.warn("Interrupted while waiting for executor shutdown");
            DEFAULT_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
