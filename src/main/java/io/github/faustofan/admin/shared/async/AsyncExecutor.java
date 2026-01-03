package io.github.faustofan.admin.shared.async;

import io.github.faustofan.admin.shared.common.AppContextHolder;
import io.github.faustofan.admin.shared.async.context.AsyncContext;
import io.github.faustofan.admin.shared.async.executor.VirtualThreadExecutorFactory;
import io.github.faustofan.admin.shared.async.executor.VirtualThreadScheduler;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 异步执行服务 - 统一对外暴露的异步API门面
 * <p>
 * 特性：
 * <ul>
 *   <li>基于JDK21虚拟线程</li>
 *   <li>自动透传MDC上下文</li>
 *   <li>自动透传应用上下文</li>
 *   <li>支持超时控制</li>
 *   <li>支持批量并发执行</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 异步执行无返回值任务
 * AsyncExecutor.runAsync(() -> {
 *     // MDC上下文会自动透传
 *     log.info("Current traceId is available in MDC");
 * });
 *
 * // 异步执行有返回值任务
 * CompletableFuture<String> future = AsyncExecutor.supplyAsync(() -> {
 *     return "result";
 * });
 *
 * // 批量并发执行
 * List<Long> ids = List.of(1L, 2L, 3L);
 * List<User> users = AsyncExecutor.mapAsync(ids, userService::findById);
 * }</pre>
 */
public final class AsyncExecutor {

    private static final Logger LOG = Logger.getLogger(AsyncExecutor.class);

    private AsyncExecutor() {
        // 禁止实例化
    }

    // ===========================
    // 基础异步执行 - 无返回值
    // ===========================

    /**
     * 异步执行任务（自动透传上下文）
     *
     * @param runnable 任务
     * @return CompletableFuture<Void>
     */
    public static CompletableFuture<Void> runAsync(Runnable runnable) {
        AsyncContext context = AppContextHolder.capture();
        return CompletableFuture.runAsync(
                () -> executeWithContext(runnable, context),
                VirtualThreadExecutorFactory.getDefault()
        );
    }

    /**
     * 异步执行任务（带超时）
     *
     * @param runnable 任务
     * @param timeout  超时时间
     * @return CompletableFuture<Void>
     */
    public static CompletableFuture<Void> runAsync(Runnable runnable, Duration timeout) {
        return runAsync(runnable).orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    // ===========================
    // 基础异步执行 - 有返回值
    // ===========================

    /**
     * 异步执行任务（自动透传上下文）
     *
     * @param supplier 任务
     * @param <T>      返回值类型
     * @return CompletableFuture<T>
     */
    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        AsyncContext context = AppContextHolder.capture();
        return CompletableFuture.supplyAsync(
                () -> executeWithContext(supplier, context),
                VirtualThreadExecutorFactory.getDefault()
        );
    }

    /**
     * 异步执行任务（带超时）
     *
     * @param supplier 任务
     * @param timeout  超时时间
     * @param <T>      返回值类型
     * @return CompletableFuture<T>
     */
    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier, Duration timeout) {
        return supplyAsync(supplier).orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * 异步执行 Callable 任务
     *
     * @param callable 任务
     * @param <T>      返回值类型
     * @return CompletableFuture<T>
     */
    public static <T> CompletableFuture<T> callAsync(Callable<T> callable) {
        AsyncContext context = AppContextHolder.capture();
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        AppContextHolder.restore(context);
                        return callable.call();
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    } finally {
                        AppContextHolder.clear();
                    }
                },
                VirtualThreadExecutorFactory.getDefault()
        );
    }

    // ===========================
    // 批量并发执行
    // ===========================

    /**
     * 并发执行多个任务，等待全部完成
     *
     * @param tasks 任务列表
     * @return 所有结果列表（保持顺序）
     */
    @SafeVarargs
    public static <T> List<T> awaitAll(Supplier<T>... tasks) {
        List<CompletableFuture<T>> futures = Arrays.stream(tasks)
                .map(AsyncExecutor::supplyAsync)
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    /**
     * 对集合中的每个元素并发执行映射操作
     *
     * @param items  元素集合
     * @param mapper 映射函数
     * @param <T>    输入类型
     * @param <R>    输出类型
     * @return 映射结果列表（保持顺序）
     */
    public static <T, R> List<R> mapAsync(Collection<T> items, Function<T, R> mapper) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }

        List<CompletableFuture<R>> futures = items.stream()
                .map(item -> supplyAsync(() -> mapper.apply(item)))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    /**
     * 对集合中的每个元素并发执行映射操作（带超时）
     *
     * @param items   元素集合
     * @param mapper  映射函数
     * @param timeout 总超时时间
     * @param <T>     输入类型
     * @param <R>     输出类型
     * @return 映射结果列表（保持顺序）
     */
    public static <T, R> List<R> mapAsync(Collection<T> items, Function<T, R> mapper, Duration timeout) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }

        List<CompletableFuture<R>> futures = items.stream()
                .map(item -> supplyAsync(() -> mapper.apply(item)))
                .toList();

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompletionException("Batch execution interrupted", e);
        } catch (ExecutionException e) {
            throw new CompletionException("Batch execution failed", e.getCause());
        } catch (TimeoutException e) {
            throw new CompletionException("Batch execution timeout after " + timeout, e);
        }

        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    /**
     * 并发执行多个任务，返回第一个成功的结果
     *
     * @param tasks 任务列表
     * @param <T>   返回值类型
     * @return 第一个成功的结果
     */
    @SafeVarargs
    public static <T> T awaitAny(Supplier<T>... tasks) {
        List<CompletableFuture<T>> futures = Arrays.stream(tasks)
                .map(AsyncExecutor::supplyAsync)
                .toList();

        return CompletableFuture.anyOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(result -> {
                    @SuppressWarnings("unchecked")
                    T typedResult = (T) result;
                    return typedResult;
                })
                .join();
    }

    // ===========================
    // 延迟执行
    // ===========================

    /**
     * 延迟执行任务
     *
     * @param runnable 任务
     * @param delay    延迟时间
     * @return ScheduledFuture
     */
    public static ScheduledFuture<?> schedule(Runnable runnable, Duration delay) {
        return VirtualThreadScheduler.schedule(runnable, delay);
    }

    /**
     * 延迟执行有返回值的任务
     *
     * @param callable 任务
     * @param delay    延迟时间
     * @param <T>      返回值类型
     * @return ScheduledFuture
     */
    public static <T> ScheduledFuture<T> schedule(Callable<T> callable, Duration delay) {
        return VirtualThreadScheduler.schedule(callable, delay);
    }

    // ===========================
    // 执行器访问
    // ===========================

    /**
     * 获取默认虚拟线程执行器
     * <p>
     * 当需要更灵活地使用执行器时可调用此方法
     *
     * @return ExecutorService
     */
    public static ExecutorService executor() {
        return VirtualThreadExecutorFactory.getDefault();
    }

    // ===========================
    // 工具方法
    // ===========================

    /**
     * 安全地获取 Future 结果（忽略异常返回null）
     *
     * @param future Future对象
     * @param <T>    返回值类型
     * @return 结果或null
     */
    public static <T> T getQuietly(Future<T> future) {
        try {
            return future.get();
        } catch (Exception e) {
            LOG.debugv(e, "Failed to get future result");
            return null;
        }
    }

    /**
     * 安全地获取 Future 结果（带超时，忽略异常返回null）
     *
     * @param future  Future对象
     * @param timeout 超时时间
     * @param <T>     返回值类型
     * @return 结果或null
     */
    public static <T> T getQuietly(Future<T> future, Duration timeout) {
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            LOG.debugv(e, "Failed to get future result within timeout");
            return null;
        }
    }

    // ===========================
    // 内部辅助方法
    // ===========================

    private static void executeWithContext(Runnable runnable, AsyncContext context) {
        try {
            AppContextHolder.restore(context);
            runnable.run();
        } finally {
            AppContextHolder.clear();
        }
    }

    private static <T> T executeWithContext(Supplier<T> supplier, AsyncContext context) {
        try {
            AppContextHolder.restore(context);
            return supplier.get();
        } finally {
            AppContextHolder.clear();
        }
    }
}
