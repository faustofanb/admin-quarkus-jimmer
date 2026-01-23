package io.github.faustofan.admin.shared.async;

import io.github.faustofan.admin.shared.async.context.AsyncContext;
import io.github.faustofan.admin.shared.common.AppContextHolder;
import io.github.faustofan.admin.shared.async.executor.VirtualThreadExecutorFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * 异步任务结果封装
 * <p>
 * 提供流式API操作异步任务，支持：
 * <ul>
 *   <li>链式调用</li>
 *   <li>异常处理</li>
 *   <li>成功/失败回调</li>
 *   <li>超时控制</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * AsyncResult<User> result = AsyncResult.of(() -> userService.findById(userId))
 *     .onSuccess(user -> log.info("Found user: {}", user.getName()))
 *     .onFailure(ex -> log.error("Failed to find user", ex))
 *     .timeout(Duration.ofSeconds(5));
 *
 * // 阻塞获取结果
 * User user = result.get();
 *
 * // 或非阻塞获取
 * result.thenAccept(user -> processUser(user));
 * }</pre>
 *
 * @param <T> 结果类型
 */
public class AsyncResult<T> {

    private final CompletableFuture<T> future;
    private final AsyncContext capturedContext;
    private final Instant createdAt;

    private AsyncResult(CompletableFuture<T> future, AsyncContext context) {
        this.future = future;
        this.capturedContext = context;
        this.createdAt = Instant.now();
    }

    // ===========================
    // 工厂方法
    // ===========================

    /**
     * 从 Supplier 创建异步结果
     *
     * @param supplier 任务
     * @param <T>      结果类型
     * @return AsyncResult
     */
    public static <T> AsyncResult<T> of(java.util.function.Supplier<T> supplier) {
        AsyncContext context = AppContextHolder.capture();
        CompletableFuture<T> future = CompletableFuture.supplyAsync(
                () -> executeWithContext(supplier, context),
                VirtualThreadExecutorFactory.getDefault()
        );
        return new AsyncResult<>(future, context);
    }

    /**
     * 从 Callable 创建异步结果
     *
     * @param callable 任务
     * @param <T>      结果类型
     * @return AsyncResult
     */
    public static <T> AsyncResult<T> ofCallable(Callable<T> callable) {
        AsyncContext context = AppContextHolder.capture();
        CompletableFuture<T> future = CompletableFuture.supplyAsync(
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
        return new AsyncResult<>(future, context);
    }

    /**
     * 创建已完成的结果
     *
     * @param value 结果值
     * @param <T>   结果类型
     * @return AsyncResult
     */
    public static <T> AsyncResult<T> completed(T value) {
        return new AsyncResult<>(CompletableFuture.completedFuture(value), AsyncContext.empty());
    }

    /**
     * 创建已失败的结果
     *
     * @param throwable 异常
     * @param <T>       结果类型
     * @return AsyncResult
     */
    public static <T> AsyncResult<T> failed(Throwable throwable) {
        return new AsyncResult<>(CompletableFuture.failedFuture(throwable), AsyncContext.empty());
    }

    // ===========================
    // 链式操作
    // ===========================

    /**
     * 成功时执行回调
     *
     * @param action 成功回调
     * @return this
     */
    public AsyncResult<T> onSuccess(Consumer<T> action) {
        future.thenAccept(action);
        return this;
    }

    /**
     * 失败时执行回调
     *
     * @param action 失败回调
     * @return this
     */
    public AsyncResult<T> onFailure(Consumer<Throwable> action) {
        future.exceptionally(ex -> {
            action.accept(ex);
            return null;
        });
        return this;
    }

    /**
     * 完成时执行回调（无论成功或失败）
     *
     * @param action 完成回调
     * @return this
     */
    public AsyncResult<T> onComplete(Runnable action) {
        future.whenComplete((result, ex) -> action.run());
        return this;
    }

    /**
     * 设置超时时间
     *
     * @param timeout 超时时间
     * @return 新的 AsyncResult
     */
    public AsyncResult<T> timeout(Duration timeout) {
        CompletableFuture<T> timeoutFuture = future.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
        return new AsyncResult<>(timeoutFuture, capturedContext);
    }

    /**
     * 映射结果
     *
     * @param mapper 映射函数
     * @param <R>    新结果类型
     * @return 新的 AsyncResult
     */
    public <R> AsyncResult<R> map(java.util.function.Function<T, R> mapper) {
        CompletableFuture<R> mappedFuture = future.thenApply(mapper);
        return new AsyncResult<>(mappedFuture, capturedContext);
    }

    /**
     * 扁平映射结果
     *
     * @param mapper 映射函数
     * @param <R>    新结果类型
     * @return 新的 AsyncResult
     */
    public <R> AsyncResult<R> flatMap(java.util.function.Function<T, AsyncResult<R>> mapper) {
        CompletableFuture<R> flatMappedFuture = future.thenCompose(result -> mapper.apply(result).future);
        return new AsyncResult<>(flatMappedFuture, capturedContext);
    }

    /**
     * 失败时恢复
     *
     * @param recovery 恢复函数
     * @return 新的 AsyncResult
     */
    public AsyncResult<T> recover(java.util.function.Function<Throwable, T> recovery) {
        CompletableFuture<T> recoveredFuture = future.exceptionally(recovery);
        return new AsyncResult<>(recoveredFuture, capturedContext);
    }

    // ===========================
    // 结果获取
    // ===========================

    /**
     * 阻塞获取结果
     *
     * @return 结果
     * @throws CompletionException 如果任务失败
     */
    public T get() {
        return future.join();
    }

    /**
     * 阻塞获取结果（带超时）
     *
     * @param timeout 超时时间
     * @return 结果
     * @throws TimeoutException 如果超时
     */
    public T get(Duration timeout) throws TimeoutException {
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompletionException("Interrupted while waiting for result", e);
        } catch (ExecutionException e) {
            throw new CompletionException(e.getCause());
        }
    }

    /**
     * 获取结果或默认值
     *
     * @param defaultValue 默认值
     * @return 结果或默认值
     */
    public T getOrDefault(T defaultValue) {
        try {
            return future.join();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 获取内部的 CompletableFuture
     *
     * @return CompletableFuture
     */
    public CompletableFuture<T> toCompletableFuture() {
        return future;
    }

    // ===========================
    // 状态查询
    // ===========================

    /**
     * 任务是否完成
     */
    public boolean isDone() {
        return future.isDone();
    }

    /**
     * 任务是否被取消
     */
    public boolean isCancelled() {
        return future.isCancelled();
    }

    /**
     * 任务是否异常完成
     */
    public boolean isCompletedExceptionally() {
        return future.isCompletedExceptionally();
    }

    /**
     * 获取创建时间
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * 取消任务
     *
     * @param mayInterruptIfRunning 是否中断执行中的任务
     * @return 是否成功取消
     */
    public boolean cancel(boolean mayInterruptIfRunning) {
        return future.cancel(mayInterruptIfRunning);
    }

    // ===========================
    // 内部辅助方法
    // ===========================

    private static <T> T executeWithContext(java.util.function.Supplier<T> supplier, AsyncContext context) {
        try {
            AppContextHolder.restore(context);
            return supplier.get();
        } finally {
            AppContextHolder.clear();
        }
    }
}
