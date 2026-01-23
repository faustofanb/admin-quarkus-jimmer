package io.github.faustofan.admin.shared.avaliable.retry;

import io.github.faustofan.admin.shared.avaliable.config.AvailabilityConfig;
import io.github.faustofan.admin.shared.avaliable.constants.AvailabilityConstants;
import io.github.faustofan.admin.shared.avaliable.constants.RetryStrategy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 重试执行器
 * <p>
 * 提供灵活的重试机制，支持多种重试策略
 */
@ApplicationScoped
public class RetryExecutor {

    private static final Logger LOG = Logger.getLogger(RetryExecutor.class);
    private static final Random RANDOM = new Random();

    private final AvailabilityConfig config;
    private final Map<String, RetryRule> rules = new ConcurrentHashMap<>();

    @Inject
    public RetryExecutor(AvailabilityConfig config) {
        this.config = config;
    }

    /**
     * 执行带重试的操作
     */
    public <T> T execute(String name, Supplier<T> supplier) throws Exception {
        return executeCallable(name, supplier::get);
    }

    /**
     * 执行带重试的操作（内部方法，避免歧义）
     */
    private <T> T executeCallable(String name, Callable<T> callable) throws Exception {
        if (!config.retry().enabled()) {
            return callable.call();
        }

        RetryRule rule = getOrCreateRule(name);
        Exception lastException = null;
        int attempt = 0;

        while (attempt <= rule.maxRetries) {
            try {
                if (attempt > 0) {
                    LOG.debugf("%s Retry attempt %d for: %s",
                            AvailabilityConstants.LogPrefix.RETRY, attempt, name);
                }

                T result = callable.call();
                if (attempt > 0) {
                    LOG.infof("%s Operation succeeded after %d retries for: %s",
                            AvailabilityConstants.LogPrefix.RETRY, attempt, name);
                }
                return result;
            } catch (Exception e) {
                lastException = e;

                if (!shouldRetry(rule, e, attempt)) {
                    LOG.debugf("%s Retry aborted for %s: exception %s not retryable or max retries reached",
                            AvailabilityConstants.LogPrefix.RETRY, name, e.getClass().getSimpleName());
                    throw e;
                }

                if (attempt < rule.maxRetries) {
                    long delay = calculateDelay(rule, attempt);
                    LOG.debugf("%s Waiting %dms before retry %d for: %s",
                            AvailabilityConstants.LogPrefix.RETRY, delay, attempt + 1, name);
                    Thread.sleep(delay);
                }

                attempt++;
            }
        }

        if (lastException != null) {
            LOG.warnf("%s All %d retries exhausted for: %s",
                    AvailabilityConstants.LogPrefix.RETRY, rule.maxRetries, name);
            throw lastException;
        }

        return null;
    }

    /**
     * 执行带重试的操作（可抛出异常）
     */
    public <T> T execute(String name, Callable<T> callable) throws Exception {
        return executeCallable(name, callable);
    }

    /**
     * 执行带重试的操作（带自定义重试条件）
     */
    public <T> T execute(String name, Callable<T> callable, Predicate<Exception> retryCondition) throws Exception {
        if (!config.retry().enabled()) {
            return callable.call();
        }

        RetryRule rule = getOrCreateRule(name);
        Exception lastException = null;
        int attempt = 0;

        while (attempt <= rule.maxRetries) {
            try {
                T result = callable.call();
                return result;
            } catch (Exception e) {
                lastException = e;

                if (!retryCondition.test(e) || attempt >= rule.maxRetries) {
                    throw e;
                }

                long delay = calculateDelay(rule, attempt);
                Thread.sleep(delay);
                attempt++;
            }
        }

        if (lastException != null) {
            throw lastException;
        }

        return null;
    }

    /**
     * 执行带重试的操作（带回退）
     */
    public <T> T executeWithFallback(String name, Supplier<T> supplier, Supplier<T> fallback) {
        try {
            return execute(name, supplier);
        } catch (Exception e) {
            LOG.debugf("%s Using fallback after retries exhausted for: %s",
                    AvailabilityConstants.LogPrefix.RETRY, name);
            return fallback.get();
        }
    }

    /**
     * 配置重试规则
     */
    public void configure(String name, int maxRetries, Duration delay, RetryStrategy strategy) {
        configure(name, maxRetries, delay, strategy, config.retry().jitter(), config.retry().maxDelay());
    }

    /**
     * 配置重试规则（完整参数）
     */
    public void configure(String name, int maxRetries, Duration delay, RetryStrategy strategy,
                          Duration jitter, Duration maxDelay) {
        rules.put(name, new RetryRule(
                maxRetries,
                delay.toMillis(),
                strategy,
                jitter.toMillis(),
                maxDelay.toMillis(),
                null,
                null
        ));
        LOG.infof("%s Configured retry for %s: maxRetries=%d, delay=%s, strategy=%s",
                AvailabilityConstants.LogPrefix.RETRY, name, maxRetries, delay, strategy.getName());
    }

    /**
     * 移除重试规则
     */
    public void remove(String name) {
        rules.remove(name);
    }

    private boolean shouldRetry(RetryRule rule, Exception e, int attempt) {
        if (attempt >= rule.maxRetries) {
            return false;
        }

        // 检查是否应该中止重试
        if (rule.abortOnExceptions != null) {
            for (Class<? extends Exception> abortClass : rule.abortOnExceptions) {
                if (abortClass.isInstance(e)) {
                    return false;
                }
            }
        }

        // 检查是否应该重试
        if (rule.retryOnExceptions != null && rule.retryOnExceptions.length > 0) {
            for (Class<? extends Exception> retryClass : rule.retryOnExceptions) {
                if (retryClass.isInstance(e)) {
                    return true;
                }
            }
            return false;
        }

        // 默认重试所有异常
        return true;
    }

    private long calculateDelay(RetryRule rule, int attempt) {
        long baseDelay = rule.delayMs;
        long delay;

        switch (rule.strategy) {
            case IMMEDIATE -> delay = 0;
            case FIXED -> delay = baseDelay;
            case EXPONENTIAL -> delay = (long) (baseDelay * Math.pow(2, attempt));
            case FIBONACCI -> delay = baseDelay * fibonacci(attempt + 1);
            case RANDOM -> delay = (long) (baseDelay * RANDOM.nextDouble() * 2);
            default -> delay = baseDelay;
        }

        // 添加抖动
        if (rule.jitterMs > 0) {
            delay += (long) (RANDOM.nextDouble() * rule.jitterMs);
        }

        // 应用最大延迟限制
        if (rule.maxDelayMs > 0 && delay > rule.maxDelayMs) {
            delay = rule.maxDelayMs;
        }

        return delay;
    }

    private long fibonacci(int n) {
        if (n <= 0) return 0;
        if (n == 1) return 1;

        long a = 0, b = 1;
        for (int i = 2; i <= n; i++) {
            long temp = a + b;
            a = b;
            b = temp;
        }
        return b;
    }

    private RetryRule getOrCreateRule(String name) {
        return rules.computeIfAbsent(name, k ->
                new RetryRule(
                        config.retry().maxRetries(),
                        config.retry().delay().toMillis(),
                        config.retry().strategy(),
                        config.retry().jitter().toMillis(),
                        config.retry().maxDelay().toMillis(),
                        null,
                        null
                )
        );
    }

    @SuppressWarnings("unchecked")
    private record RetryRule(
            int maxRetries,
            long delayMs,
            RetryStrategy strategy,
            long jitterMs,
            long maxDelayMs,
            Class<? extends Exception>[] retryOnExceptions,
            Class<? extends Exception>[] abortOnExceptions
    ) {}
}
