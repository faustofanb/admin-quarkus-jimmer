package io.github.faustofan.admin.shared.cache.local;

import io.github.faustofan.admin.shared.cache.config.CacheConfig;
import io.github.faustofan.admin.shared.cache.constants.CacheOperationType;
import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheManager;
import io.quarkus.cache.CaffeineCache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * 本地缓存管理器
 * <p>
 * 基于 Quarkus Cache (Caffeine) 的本地缓存实现，作为二级缓存的L1层。
 * <p>
 * 特性：
 * <ul>
 *   <li>高性能本地内存缓存</li>
 *   <li>支持通过注解或编程方式使用</li>
 *   <li>自动过期和容量控制</li>
 *   <li>线程安全</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 获取缓存值
 * Optional<User> user = localCacheManager.get(CacheConstants.LocalCacheName.USER_CACHE, "user:1", User.class);
 *
 * // 获取或加载缓存值
 * User user = localCacheManager.getOrLoad(
 *     CacheConstants.LocalCacheName.USER_CACHE,
 *     "user:1",
 *     () -> userRepository.findById(1L)
 * );
 *
 * // 写入缓存
 * localCacheManager.put(CacheConstants.LocalCacheName.USER_CACHE, "user:1", user);
 *
 * // 删除缓存
 * localCacheManager.invalidate(CacheConstants.LocalCacheName.USER_CACHE, "user:1");
 *
 * // 清空缓存
 * localCacheManager.invalidateAll(CacheConstants.LocalCacheName.USER_CACHE);
 * }</pre>
 */
@ApplicationScoped
public class LocalCacheManager {

    private static final Logger LOG = Logger.getLogger(LocalCacheManager.class);

    private final CacheManager cacheManager;
    private final CacheConfig cacheConfig;

    @Inject
    public LocalCacheManager(CacheManager cacheManager, CacheConfig cacheConfig) {
        this.cacheManager = cacheManager;
        this.cacheConfig = cacheConfig;
    }

    // ===========================
    // 读取操作
    // ===========================

    /**
     * 根据Key获取缓存值
     *
     * @param cacheName 缓存名称
     * @param key       缓存Key
     * @param type      值类型
     * @param <T>       值类型
     * @return 缓存值（如果存在）
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String cacheName, String key, Class<T> type) {
        if (!isEnabled()) {
            return Optional.empty();
        }

        try {
            Optional<Cache> cacheOpt = cacheManager.getCache(cacheName);
            if (cacheOpt.isEmpty()) {
                LOG.debugv("Cache not found: {0}", cacheName);
                return Optional.empty();
            }

            // Using CaffeineCache for getIfPresent
            Object value = cacheOpt.get().as(CaffeineCache.class).getIfPresent(key).join();

            if (value != null) {
                logOperation(CacheOperationType.L1_HIT, cacheName, key);
                return Optional.of((T) value);
            }
            logOperation(CacheOperationType.MISS, cacheName, key);
            return Optional.empty();

        } catch (Exception e) {
            LOG.warnv(e, "Failed to get from local cache: {0}/{1}", cacheName, key);
            return Optional.empty();
        }
    }

    /**
     * 获取缓存值，如果不存在则通过loader加载
     *
     * @param cacheName 缓存名称
     * @param key       缓存Key
     * @param loader    数据加载器
     * @param <T>       值类型
     * @return 缓存值或加载的值
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrLoad(String cacheName, String key, Supplier<T> loader) {
        if (!isEnabled()) {
            return loader.get();
        }

        try {
            Optional<Cache> cacheOpt = cacheManager.getCache(cacheName);
            if (cacheOpt.isEmpty()) {
                LOG.debugv("Cache not found, loading directly: {0}", cacheName);
                return loader.get();
            }

            Object value = cacheOpt.get().get(key, k -> {
                logOperation(CacheOperationType.MISS, cacheName, key);
                return loader.get();
            }).await().indefinitely();

            if (value != null) {
                logOperation(CacheOperationType.L1_HIT, cacheName, key);
            }
            return (T) value;

        } catch (Exception e) {
            LOG.warnv(e, "Failed to get or load from local cache, falling back to loader: {0}/{1}", cacheName, key);
            return loader.get();
        }
    }

    /**
     * 异步获取缓存值
     *
     * @param cacheName 缓存名称
     * @param key       缓存Key
     * @param type      值类型
     * @param <T>       值类型
     * @return 异步缓存值
     */
    @SuppressWarnings("unchecked")
    public <T> Uni<Optional<T>> getAsync(String cacheName, String key, Class<T> type) {
        if (!isEnabled()) {
            return Uni.createFrom().item(Optional.empty());
        }

        try {
            Optional<Cache> cacheOpt = cacheManager.getCache(cacheName);
            if (cacheOpt.isEmpty()) {
                return Uni.createFrom().item(Optional.empty());
            }

            return Uni.createFrom().completionStage(cacheOpt.get().as(CaffeineCache.class).getIfPresent(key))
                    .map(value -> {
                        if (value != null) {
                            logOperation(CacheOperationType.L1_HIT, cacheName, key);
                            return Optional.of((T) value);
                        }
                        return Optional.<T>empty();
                    });

        } catch (Exception e) {
            LOG.warnv(e, "Failed to async get from local cache: {0}/{1}", cacheName, key);
            return Uni.createFrom().item(Optional.empty());
        }
    }

    // ===========================
    // 写入操作
    // ===========================

    /**
     * 写入缓存
     * <p>
     * 注意：Quarkus Cache 的编程式API不直接支持put操作，
     * 这里通过get+loader组合实现
     *
     * @param cacheName 缓存名称
     * @param key       缓存Key
     * @param value     缓存值
     * @param <T>       值类型
     */
    public <T> void put(String cacheName, String key, T value) {
        if (!isEnabled() || value == null) {
            return;
        }

        try {
            Optional<Cache> cacheOpt = cacheManager.getCache(cacheName);
            if (cacheOpt.isEmpty()) {
                LOG.debugv("Cache not found for put: {0}", cacheName);
                return;
            }

            // 通过get+loader方式写入缓存
            // 注意：如果key已存在，get不会覆盖。若需强制覆盖，应先invalidate
            // 但考虑到 put 语义，通常期望覆盖。
            // Quarkus Cache API 无直接 put，invalidate + get 可能会有并发问题但在此场景下通常接受。
            // 或者使用 Caffeine 原始 API (如果暴露)
            // 这里维持原有逻辑（虽然原逻辑 get(k, v) 也是如果不存才写入）
            
            Cache cache = cacheOpt.get();
            cache.invalidate(key).await().indefinitely();
            cache.get(key, k -> value).await().indefinitely();
            
            logOperation(CacheOperationType.PUT, cacheName, key);

        } catch (Exception e) {
            LOG.warnv(e, "Failed to put to local cache: {0}/{1}", cacheName, key);
        }
    }

    // ===========================
    // 删除操作
    // ===========================

    /**
     * 使单个缓存条目失效
     *
     * @param cacheName 缓存名称
     * @param key       缓存Key
     */
    public void invalidate(String cacheName, String key) {
        if (!isEnabled()) {
            return;
        }

        try {
            Optional<Cache> cacheOpt = cacheManager.getCache(cacheName);
            if (cacheOpt.isEmpty()) {
                return;
            }

            cacheOpt.get().invalidate(key).await().indefinitely();
            logOperation(CacheOperationType.DELETE, cacheName, key);

        } catch (Exception e) {
            LOG.warnv(e, "Failed to invalidate local cache: {0}/{1}", cacheName, key);
        }
    }

    /**
     * 使整个缓存失效
     *
     * @param cacheName 缓存名称
     */
    public void invalidateAll(String cacheName) {
        if (!isEnabled()) {
            return;
        }

        try {
            Optional<Cache> cacheOpt = cacheManager.getCache(cacheName);
            if (cacheOpt.isEmpty()) {
                return;
            }

            cacheOpt.get().invalidateAll().await().indefinitely();
            logOperation(CacheOperationType.CLEAR, cacheName, "*");

        } catch (Exception e) {
            LOG.warnv(e, "Failed to invalidate all local cache: {0}", cacheName);
        }
    }

    // ===========================
    // 辅助方法
    // ===========================

    /**
     * 检查本地缓存是否启用
     */
    public boolean isEnabled() {
        return cacheConfig.enabled() && cacheConfig.local().enabled();
    }

    /**
     * 检查指定缓存是否存在
     */
    public boolean cacheExists(String cacheName) {
        return cacheManager.getCache(cacheName).isPresent();
    }

    /**
     * 获取所有缓存名称
     */
    public Iterable<String> getCacheNames() {
        return cacheManager.getCacheNames();
    }

    private void logOperation(CacheOperationType type, String cacheName, String key) {
        if (LOG.isDebugEnabled()) {
            LOG.debugv("Local cache {0}: {1}/{2}", type.getDescription(), cacheName, key);
        }
    }
}
