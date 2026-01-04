package io.github.faustofan.admin.shared.avaliable.fallback;

import io.github.faustofan.admin.shared.avaliable.constants.AvailabilityConstants;
import io.github.faustofan.admin.shared.avaliable.constants.FallbackType;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 回退处理器
 * <p>
 * 提供统一的回退（Fallback）处理能力，支持多种回退策略
 */
@ApplicationScoped
public class FallbackHandler {

    private static final Logger LOG = Logger.getLogger(FallbackHandler.class);

    /**
     * 注册的回退函数
     */
    private final Map<String, Function<Throwable, ?>> fallbackFunctions = new ConcurrentHashMap<>();

    /**
     * 缓存的最后成功结果（用于 CACHED 策略）
     */
    private final Map<String, Object> cachedResults = new ConcurrentHashMap<>();

    /**
     * 注册回退函数
     *
     * @param name     资源名称
     * @param fallback 回退函数
     * @param <T>      返回值类型
     */
    public <T> void register(String name, Function<Throwable, T> fallback) {
        fallbackFunctions.put(name, fallback);
        LOG.infof("%s Registered fallback for: %s",
                AvailabilityConstants.LogPrefix.FALLBACK, name);
    }

    /**
     * 注册简单回退值
     *
     * @param name         资源名称
     * @param defaultValue 默认回退值
     * @param <T>          返回值类型
     */
    public <T> void registerDefault(String name, T defaultValue) {
        fallbackFunctions.put(name, throwable -> defaultValue);
        LOG.infof("%s Registered default fallback value for: %s",
                AvailabilityConstants.LogPrefix.FALLBACK, name);
    }

    /**
     * 执行回退
     *
     * @param name      资源名称
     * @param throwable 触发回退的异常
     * @param <T>       返回值类型
     * @return 回退结果
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> execute(String name, Throwable throwable) {
        Function<Throwable, ?> fallback = fallbackFunctions.get(name);
        if (fallback == null) {
            LOG.debugf("%s No fallback registered for: %s",
                    AvailabilityConstants.LogPrefix.FALLBACK, name);
            return Optional.empty();
        }

        try {
            T result = (T) fallback.apply(throwable);
            LOG.debugf("%s Executed fallback for %s, cause: %s",
                    AvailabilityConstants.LogPrefix.FALLBACK, name,
                    throwable != null ? throwable.getClass().getSimpleName() : "null");
            return Optional.ofNullable(result);
        } catch (Exception e) {
            LOG.warnf("%s Fallback execution failed for %s: %s",
                    AvailabilityConstants.LogPrefix.FALLBACK, name, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 执行操作并在失败时回退
     *
     * @param name     资源名称
     * @param supplier 主要操作
     * @param <T>      返回值类型
     * @return 操作结果或回退结果
     */
    public <T> T executeWithFallback(String name, Supplier<T> supplier) {
        return executeWithFallback(name, supplier, FallbackType.DEFAULT_VALUE);
    }

    /**
     * 执行操作并在失败时使用指定策略回退
     *
     * @param name         资源名称
     * @param supplier     主要操作
     * @param fallbackType 回退类型
     * @param <T>          返回值类型
     * @return 操作结果或回退结果
     */
    @SuppressWarnings("unchecked")
    public <T> T executeWithFallback(String name, Supplier<T> supplier, FallbackType fallbackType) {
        try {
            T result = supplier.get();
            // 缓存成功结果
            if (result != null) {
                cachedResults.put(name, result);
            }
            return result;
        } catch (Exception e) {
            LOG.debugf("%s Operation failed for %s, applying fallback type: %s",
                    AvailabilityConstants.LogPrefix.FALLBACK, name, fallbackType.getName());

            return switch (fallbackType) {
                case EMPTY -> null;
                case DEFAULT_VALUE -> this.<T>execute(name, e).orElse(null);
                case CACHED -> (T) cachedResults.get(name);
                case DEGRADED -> this.<T>execute(name, e).orElse(null);
                case THROW_EXCEPTION -> {
                    if (e instanceof RuntimeException) {
                        throw (RuntimeException) e;
                    }
                    throw new RuntimeException("Operation failed", e);
                }
                case REDIRECT -> this.<T>execute(name, e).orElseThrow(() -> {
                    if (e instanceof RuntimeException) {
                        return (RuntimeException) e;
                    }
                    return new RuntimeException("Operation failed", e);
                });
            };
        }
    }

    /**
     * 获取空值回退（针对集合类型）
     */
    @SuppressWarnings("unchecked")
    public <T> T getEmptyFallback(Class<T> type) {
        if (List.class.isAssignableFrom(type)) {
            return (T) Collections.emptyList();
        }
        if (Map.class.isAssignableFrom(type)) {
            return (T) Collections.emptyMap();
        }
        if (Optional.class.isAssignableFrom(type)) {
            return (T) Optional.empty();
        }
        return null;
    }

    /**
     * 获取缓存的结果
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getCachedResult(String name) {
        return Optional.ofNullable((T) cachedResults.get(name));
    }

    /**
     * 设置缓存结果
     */
    public <T> void setCachedResult(String name, T result) {
        if (result != null) {
            cachedResults.put(name, result);
        }
    }

    /**
     * 清除缓存结果
     */
    public void clearCachedResult(String name) {
        cachedResults.remove(name);
    }

    /**
     * 清除所有缓存结果
     */
    public void clearAllCachedResults() {
        cachedResults.clear();
    }

    /**
     * 移除回退函数
     */
    public void unregister(String name) {
        fallbackFunctions.remove(name);
    }

    /**
     * 检查是否有注册的回退
     */
    public boolean hasFallback(String name) {
        return fallbackFunctions.containsKey(name);
    }
}
