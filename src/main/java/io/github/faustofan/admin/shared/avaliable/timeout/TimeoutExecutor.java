package io.github.faustofan.admin.shared.avaliable.timeout;

import io.github.faustofan.admin.shared.avaliable.config.AvailabilityConfig;
import io.github.faustofan.admin.shared.avaliable.constants.AvailabilityConstants;
import io.github.faustofan.admin.shared.avaliable.exception.OperationTimeoutException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * 超时执行器
 * <p>
 * 提供操作超时控制能力
 */
@ApplicationScoped
public class TimeoutExecutor {

    private static final Logger LOG = Logger.getLogger(TimeoutExecutor.class);

    private final AvailabilityConfig config;
    private final Map<String, Duration> timeoutRules = new ConcurrentHashMap<>();
    private final ExecutorService executor;

    @Inject
    public TimeoutExecutor(AvailabilityConfig config) {
        this.config = config;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 执行带超时控制的操作
     */
    public <T> T execute(String name, Supplier<T> supplier) {
        return execute(name, supplier, getTimeout(name));
    }

    /**
     * 执行带超时控制的操作（自定义超时时间）
     */
    public <T> T execute(String name, Supplier<T> supplier, Duration timeout) {
        if (!config.timeout().enabled()) {
            return supplier.get();
        }

        long startTime = System.currentTimeMillis();
        Future<T> future = executor.submit(supplier::get);

        try {
            T result = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            long elapsed = System.currentTimeMillis() - startTime;

            if (config.timeout().metricsEnabled()) {
                LOG.debugf("%s Operation %s completed in %dms (timeout: %s)",
                        AvailabilityConstants.LogPrefix.TIMEOUT, name, elapsed, timeout);
            }

            return result;
        } catch (TimeoutException e) {
            future.cancel(true);
            long elapsed = System.currentTimeMillis() - startTime;
            LOG.warnf("%s Operation %s timed out after %dms (timeout: %s)",
                    AvailabilityConstants.LogPrefix.TIMEOUT, name, elapsed, timeout);
            throw new OperationTimeoutException(name, timeout, elapsed);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Operation interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException("Operation failed", cause);
        }
    }

    /**
     * 执行带超时控制的操作（可抛出异常）
     */
    public <T> T execute(String name, Callable<T> callable) throws Exception {
        return execute(name, callable, getTimeout(name));
    }

    /**
     * 执行带超时控制的操作（可抛出异常，自定义超时时间）
     */
    public <T> T execute(String name, Callable<T> callable, Duration timeout) throws Exception {
        if (!config.timeout().enabled()) {
            return callable.call();
        }

        long startTime = System.currentTimeMillis();
        Future<T> future = executor.submit(callable);

        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            long elapsed = System.currentTimeMillis() - startTime;
            throw new OperationTimeoutException(name, timeout, elapsed);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw new RuntimeException("Operation failed", cause);
        }
    }

    /**
     * 执行带超时控制的操作（带回退）
     */
    public <T> T executeWithFallback(String name, Supplier<T> supplier, Supplier<T> fallback) {
        return executeWithFallback(name, supplier, fallback, getTimeout(name));
    }

    /**
     * 执行带超时控制的操作（带回退，自定义超时时间）
     */
    public <T> T executeWithFallback(String name, Supplier<T> supplier, Supplier<T> fallback, Duration timeout) {
        try {
            return execute(name, supplier, timeout);
        } catch (OperationTimeoutException e) {
            LOG.debugf("%s Using fallback after timeout for: %s",
                    AvailabilityConstants.LogPrefix.TIMEOUT, name);
            return fallback.get();
        }
    }

    /**
     * 异步执行带超时控制的操作
     */
    public <T> CompletableFuture<T> executeAsync(String name, Supplier<T> supplier) {
        return executeAsync(name, supplier, getTimeout(name));
    }

    /**
     * 异步执行带超时控制的操作（自定义超时时间）
     */
    public <T> CompletableFuture<T> executeAsync(String name, Supplier<T> supplier, Duration timeout) {
        if (!config.timeout().enabled()) {
            return CompletableFuture.supplyAsync(supplier, executor);
        }

        return CompletableFuture.supplyAsync(supplier, executor)
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(e -> {
                    if (e instanceof TimeoutException || e.getCause() instanceof TimeoutException) {
                        throw new OperationTimeoutException(name, timeout);
                    }
                    if (e instanceof RuntimeException) {
                        throw (RuntimeException) e;
                    }
                    throw new RuntimeException(e);
                });
    }

    /**
     * 配置超时规则
     */
    public void configure(String name, Duration timeout) {
        timeoutRules.put(name, timeout);
        LOG.infof("%s Configured timeout for %s: %s",
                AvailabilityConstants.LogPrefix.TIMEOUT, name, timeout);
    }

    /**
     * 移除超时规则
     */
    public void remove(String name) {
        timeoutRules.remove(name);
    }

    /**
     * 获取超时时间
     */
    public Duration getTimeout(String name) {
        return timeoutRules.getOrDefault(name, config.timeout().defaultDuration());
    }
}
