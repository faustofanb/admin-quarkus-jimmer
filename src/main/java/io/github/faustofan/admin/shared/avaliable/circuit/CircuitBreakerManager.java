package io.github.faustofan.admin.shared.avaliable.circuit;

import io.github.faustofan.admin.shared.avaliable.config.AvailabilityConfig;
import io.github.faustofan.admin.shared.avaliable.constants.AvailabilityConstants;
import io.github.faustofan.admin.shared.avaliable.constants.CircuitBreakerState;
import io.github.faustofan.admin.shared.avaliable.exception.CircuitBreakerOpenException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 熔断器管理器
 * <p>
 * 管理所有熔断器实例，提供熔断器的创建、查询和操作API。
 * <p>
 * 熔断器状态转换：
 * <ul>
 *   <li>CLOSED → OPEN: 当失败率超过阈值时</li>
 *   <li>OPEN → HALF_OPEN: 当延迟时间过后</li>
 *   <li>HALF_OPEN → CLOSED: 当连续成功次数达到阈值时</li>
 *   <li>HALF_OPEN → OPEN: 当有请求失败时</li>
 * </ul>
 */
@ApplicationScoped
public class CircuitBreakerManager {

    private static final Logger LOG = Logger.getLogger(CircuitBreakerManager.class);

    private final AvailabilityConfig config;
    private final Map<String, CircuitBreakerContext> circuitBreakers = new ConcurrentHashMap<>();
    private final Map<String, CircuitBreakerRule> rules = new ConcurrentHashMap<>();

    @Inject
    public CircuitBreakerManager(AvailabilityConfig config) {
        this.config = config;
    }

    /**
     * 获取或创建熔断器
     */
    public CircuitBreakerContext getOrCreate(String name) {
        return circuitBreakers.computeIfAbsent(name, CircuitBreakerContext::new);
    }

    /**
     * 获取熔断器（如果存在）
     */
    public Optional<CircuitBreakerContext> get(String name) {
        return Optional.ofNullable(circuitBreakers.get(name));
    }

    /**
     * 检查熔断器是否允许请求通过
     */
    public boolean isRequestAllowed(String name) {
        if (!config.circuitBreaker().enabled()) {
            return true;
        }

        CircuitBreakerContext context = getOrCreate(name);
        CircuitBreakerRule rule = getOrCreateRule(name);

        return switch (context.getState()) {
            case CLOSED -> true;
            case OPEN -> {
                // 检查是否应该转换到半开状态
                if (context.getStateDurationMs() >= rule.delayMs) {
                    transitionTo(context, CircuitBreakerState.HALF_OPEN);
                    yield true; // 允许一个探测请求
                }
                context.incrementRejected();
                yield false;
            }
            case HALF_OPEN -> true; // 半开状态允许请求通过进行测试
        };
    }

    /**
     * 执行带熔断保护的操作
     */
    public <T> T execute(String name, Supplier<T> supplier) {
        return execute(name, supplier, null);
    }

    /**
     * 执行带熔断保护的操作（带回退）
     */
    public <T> T execute(String name, Supplier<T> supplier, Supplier<T> fallback) {
        if (!isRequestAllowed(name)) {
            if (fallback != null) {
                LOG.debugf("%s Circuit breaker %s is open, using fallback",
                        AvailabilityConstants.LogPrefix.CIRCUIT_BREAKER, name);
                return fallback.get();
            }
            throw new CircuitBreakerOpenException(name);
        }

        CircuitBreakerContext context = getOrCreate(name);

        try {
            T result = supplier.get();
            onSuccess(name);
            return result;
        } catch (Exception e) {
            onFailure(name);
            if (fallback != null) {
                return fallback.get();
            }
            throw e;
        }
    }

    /**
     * 记录成功
     */
    public void onSuccess(String name) {
        if (!config.circuitBreaker().enabled()) {
            return;
        }

        CircuitBreakerContext context = getOrCreate(name);
        CircuitBreakerRule rule = getOrCreateRule(name);

        context.incrementSuccess();

        if (context.getState() == CircuitBreakerState.HALF_OPEN) {
            // 在半开状态，检查是否应该关闭熔断器
            if (context.getSuccessCount() >= rule.successThreshold) {
                transitionTo(context, CircuitBreakerState.CLOSED);
            }
        }
    }

    /**
     * 记录失败
     */
    public void onFailure(String name) {
        if (!config.circuitBreaker().enabled()) {
            return;
        }

        CircuitBreakerContext context = getOrCreate(name);
        CircuitBreakerRule rule = getOrCreateRule(name);

        context.incrementFailure();

        switch (context.getState()) {
            case CLOSED -> {
                // 检查是否应该打开熔断器
                if (context.getTotalCount() >= rule.requestVolumeThreshold
                        && context.getFailureRatio() >= rule.failureRatio) {
                    transitionTo(context, CircuitBreakerState.OPEN);
                }
            }
            case HALF_OPEN -> {
                // 半开状态有一次失败就重新打开
                transitionTo(context, CircuitBreakerState.OPEN);
            }
            default -> {
                // OPEN 状态不需要处理
            }
        }
    }

    /**
     * 强制打开熔断器
     */
    public void forceOpen(String name) {
        CircuitBreakerContext context = getOrCreate(name);
        transitionTo(context, CircuitBreakerState.OPEN);
    }

    /**
     * 强制关闭熔断器
     */
    public void forceClose(String name) {
        CircuitBreakerContext context = getOrCreate(name);
        transitionTo(context, CircuitBreakerState.CLOSED);
    }

    /**
     * 重置熔断器
     */
    public void reset(String name) {
        CircuitBreakerContext context = circuitBreakers.get(name);
        if (context != null) {
            context.reset();
            LOG.infof("%s Circuit breaker %s has been reset",
                    AvailabilityConstants.LogPrefix.CIRCUIT_BREAKER, name);
        }
    }

    /**
     * 重置所有熔断器
     */
    public void resetAll() {
        circuitBreakers.values().forEach(CircuitBreakerContext::reset);
    }

    /**
     * 配置熔断器规则
     */
    public void configure(String name, double failureRatio, int requestVolumeThreshold,
                          Duration delay, int successThreshold) {
        rules.put(name, new CircuitBreakerRule(
                failureRatio, requestVolumeThreshold, delay.toMillis(), successThreshold
        ));
        LOG.infof("%s Configured circuit breaker %s: failureRatio=%.2f, volumeThreshold=%d, delay=%s, successThreshold=%d",
                AvailabilityConstants.LogPrefix.CIRCUIT_BREAKER, name,
                failureRatio, requestVolumeThreshold, delay, successThreshold);
    }

    /**
     * 获取所有熔断器状态
     */
    public Map<String, CircuitBreakerContext> getAllContexts() {
        return Map.copyOf(circuitBreakers);
    }

    /**
     * 获取指定熔断器的状态
     */
    public CircuitBreakerState getState(String name) {
        CircuitBreakerContext context = circuitBreakers.get(name);
        return context != null ? context.getState() : CircuitBreakerState.CLOSED;
    }

    private void transitionTo(CircuitBreakerContext context, CircuitBreakerState newState) {
        CircuitBreakerState oldState = context.getState();
        context.setState(newState);

        if (newState == CircuitBreakerState.CLOSED) {
            context.resetStats();
        }

        LOG.infof("%s Circuit breaker %s transitioned from %s to %s",
                AvailabilityConstants.LogPrefix.CIRCUIT_BREAKER,
                context.getName(), oldState.getName(), newState.getName());
    }

    private CircuitBreakerRule getOrCreateRule(String name) {
        return rules.computeIfAbsent(name, k ->
                new CircuitBreakerRule(
                        config.circuitBreaker().failureRatio(),
                        config.circuitBreaker().requestVolumeThreshold(),
                        config.circuitBreaker().delay().toMillis(),
                        config.circuitBreaker().successThreshold()
                )
        );
    }

    private record CircuitBreakerRule(
            double failureRatio,
            int requestVolumeThreshold,
            long delayMs,
            int successThreshold
    ) {}
}
