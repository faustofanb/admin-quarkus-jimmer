package io.github.faustofan.admin.shared.cache;

import io.github.faustofan.admin.shared.cache.constants.CacheConstants;
import io.github.faustofan.admin.shared.cache.constants.CacheOperationType;
import io.github.faustofan.admin.shared.cache.constants.CacheStrategy;
import io.github.faustofan.admin.shared.cache.config.CacheConfig;
import io.github.faustofan.admin.shared.cache.exception.CacheException;
import io.github.faustofan.admin.shared.cache.exception.CacheExceptionType;
import io.github.faustofan.admin.shared.cache.local.LocalCacheManager;
import io.github.faustofan.admin.shared.cache.redis.RedisBloomFilter;
import io.github.faustofan.admin.shared.cache.redis.RedisCacheManager;
import io.github.faustofan.admin.shared.distributed.constants.DistributedConstants;
import io.github.faustofan.admin.shared.distributed.lock.LockContext;
import io.github.faustofan.admin.shared.distributed.lock.LockProvider;
import io.github.faustofan.admin.shared.distributed.lock.RedisLockProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 二级缓存统一门面（Cache Facade）
 * <p>
 * 根据配置的 {@link CacheStrategy} 自动选择本地缓存（L1）和 Redis（L2）
 * 的读写路径，统一对外提供缓存 API。
 * <p>
 * 主要特性：
 * <ul>
 *   <li>二级缓存（本地 + Redis）</li>
 *   <li>防止缓存穿透（空值占位）</li>
 *   <li>防止缓存雪崩（TTL 随机抖动）</li>
 *   <li>布隆过滤器快速判断是否可能存在</li>
 *   <li>统一日志/监控（CacheOperationType）</li>
 * </ul>
 */
@ApplicationScoped
public class CacheFacade {

    private static final Logger LOG = Logger.getLogger(CacheFacade.class);

    private final LocalCacheManager localCacheManager;
    private final RedisCacheManager redisCacheManager;
    private final RedisBloomFilter bloomFilter;
    private final CacheConfig cacheConfig;
    private final RedisLockProvider lockProvider;

    @Inject
    public CacheFacade(LocalCacheManager localCacheManager,
                       RedisCacheManager redisCacheManager,
                       RedisBloomFilter bloomFilter,
                       CacheConfig cacheConfig,
                       RedisLockProvider lockProvider) {
        this.localCacheManager = localCacheManager;
        this.redisCacheManager = redisCacheManager;
        this.bloomFilter = bloomFilter;
        this.cacheConfig = cacheConfig;
        this.lockProvider = lockProvider;
    }

    // ===========================
    // 读取操作
    // ===========================

    /**
     * 统一获取缓存值（根据策略自动选择 L1/L2）
     *
     * @param key 业务层提供的业务键（不含全局前缀）
     * @param type 目标类型
     * @param <T> 类型参数
     * @return Optional 包含缓存值，若不存在返回 empty
     */
    public <T> Optional<T> get(String key, Class<T> type) {
        String fullKey = buildFullKey(key);
        CacheStrategy strategy = cacheConfig.defaultStrategy();
        switch (strategy) {
            case LOCAL_ONLY:
                return localCacheManager.get(CacheConstants.LocalCacheName.GENERIC_CACHE, fullKey, type);
            case REDIS_ONLY:
                return redisCacheManager.get(fullKey, type);
            case TWO_LEVEL:
                // 先查询本地缓存
                Optional<T> local = localCacheManager.get(CacheConstants.LocalCacheName.GENERIC_CACHE, fullKey, type);
                if (local.isPresent()) {
                    logOperation(CacheOperationType.L1_HIT, fullKey);
                    return local;
                }
                // 本地未命中，查询 Redis
                Optional<T> redis = redisCacheManager.get(fullKey, type);
                if (redis.isPresent()) {
                    // 写回本地缓存提升后续命中率
                    localCacheManager.put(CacheConstants.LocalCacheName.GENERIC_CACHE, fullKey, redis.get());
                    logOperation(CacheOperationType.L2_HIT, fullKey);
                } else {
                    logOperation(CacheOperationType.MISS, fullKey);
                }
                return redis;
            case READ_WRITE_THROUGH:
                // 读取时直接走 Redis（保证一致性），并写入本地缓存
                Optional<T> rw = redisCacheManager.get(fullKey, type);
                rw.ifPresent(v -> localCacheManager.put(CacheConstants.LocalCacheName.GENERIC_CACHE, fullKey, v));
                return rw;
            case WRITE_BEHIND:
                // 读取同两级缓存，写入时只写本地，后续异步写回（此处仅实现同步写入）
                Optional<T> wb = localCacheManager.get(CacheConstants.LocalCacheName.GENERIC_CACHE, fullKey, type);
                if (wb.isPresent()) {
                    return wb;
                }
                // 本地未命中，尝试 Redis
                Optional<T> wbRedis = redisCacheManager.get(fullKey, type);
                wbRedis.ifPresent(v -> localCacheManager.put(CacheConstants.LocalCacheName.GENERIC_CACHE, fullKey, v));
                return wbRedis;
            default:
                return Optional.empty();
        }
    }

    /**
     * 读取或加载（Cache Aside）
     *
     * @param key 业务键
     * @param type 目标类型
     * @param loader 数据加载器（当缓存未命中时调用）
     * @param ttl 过期时间（若为 null 使用默认）
     * @param <T> 类型
     * @return 缓存值或加载的值
     */
    public <T> T getOrLoad(String key, Class<T> type, Supplier<T> loader, Duration ttl) {
        String fullKey = buildFullKey(key);
        // 布隆过滤器快速过滤（仅在使用两级或 Redis 相关策略时）
        if (cacheConfig.bloomFilter().enabled() && !bloomFilter.mightContain(key, key)) {
            // 直接返回加载结果，避免 DB 查询
            T value = loader.get();
            // 写入缓存（根据策略）
            put(fullKey, value, ttl);
            return value;
        }
        Optional<T> cached = get(fullKey, type);
        if (cached.isPresent()) {
            return cached.get();
        }
        // 缓存未命中，加载
        T value = loader.get();
        put(fullKey, value, ttl);
        return value;
    }

    // ===========================
    // 写入操作
    // ===========================

    /**
     * 统一写入缓存（根据策略）
     *
     * @param key 完整业务键（不含前缀）
     * @param value 值（null 表示写入空占位）
     * @param ttl 过期时间，null 使用默认 TTL
     * @param <T> 类型
     */
    public <T> void put(String key, T value, Duration ttl) {
        String fullKey = buildFullKey(key);
        CacheStrategy strategy = cacheConfig.defaultStrategy();
        Duration effectiveTtl = ttl != null ? ttl : cacheConfig.defaultTtl();
        switch (strategy) {
            case LOCAL_ONLY:
                localCacheManager.put(CacheConstants.LocalCacheName.GENERIC_CACHE, fullKey, value);
                break;
            case REDIS_ONLY:
                redisCacheManager.set(fullKey, value, effectiveTtl);
                break;
            case TWO_LEVEL:
                // 先写 Redis，再写本地（本地缓存保持最新）
                redisCacheManager.set(fullKey, value, effectiveTtl);
                localCacheManager.put(CacheConstants.LocalCacheName.GENERIC_CACHE, fullKey, value);
                break;
            case READ_WRITE_THROUGH:
                // 写入 Redis 并同步写本地
                redisCacheManager.set(fullKey, value, effectiveTtl);
                localCacheManager.put(CacheConstants.LocalCacheName.GENERIC_CACHE, fullKey, value);
                break;
            case WRITE_BEHIND:
                // 只写本地，异步写回 Redis（此处同步写入以保证功能）
                localCacheManager.put(CacheConstants.LocalCacheName.GENERIC_CACHE, fullKey, value);
                // 简单实现：立即写入 Redis（实际可改为异步队列）
                redisCacheManager.set(fullKey, value, effectiveTtl);
                break;
            default:
                LOG.warnv("Unsupported cache strategy: {0}", strategy);
        }
        logOperation(CacheOperationType.PUT, fullKey);
    }

    // ===========================
    // 删除操作
    // ===========================

    public void invalidate(String key) {
        String fullKey = buildFullKey(key);
        CacheStrategy strategy = cacheConfig.defaultStrategy();
        switch (strategy) {
            case LOCAL_ONLY:
                localCacheManager.invalidate(CacheConstants.LocalCacheName.GENERIC_CACHE, fullKey);
                break;
            case REDIS_ONLY:
                redisCacheManager.delete(fullKey);
                break;
            case TWO_LEVEL:
            case READ_WRITE_THROUGH:
            case WRITE_BEHIND:
                localCacheManager.invalidate(CacheConstants.LocalCacheName.GENERIC_CACHE, fullKey);
                redisCacheManager.delete(fullKey);
                break;
            default:
                LOG.warnv("Unsupported cache strategy for invalidate: {0}", strategy);
        }
        logOperation(CacheOperationType.DELETE, fullKey);
    }

    public void invalidateAll() {
        // 清空本地缓存
        localCacheManager.invalidateAll(CacheConstants.LocalCacheName.GENERIC_CACHE);
        // 清空 Redis（仅删除 admin:* 前缀的键）
        redisCacheManager.deleteByPattern("*");
        logOperation(CacheOperationType.CLEAR, "all");
    }

    // ===========================
    // 防击穿操作（带分布式锁）
    // ===========================

    /**
     * 带分布式锁的缓存读取或加载（防止缓存击穿）
     * <p>
     * 当缓存未命中时，使用分布式锁保护，只允许一个线程加载数据，
     * 其他线程等待并复用加载结果。
     * <p>
     * 适用场景：
     * <ul>
     *   <li>热点Key的缓存加载</li>
     *   <li>数据库压力较大的场景</li>
     *   <li>缓存失效瞬间的高并发访问</li>
     * </ul>
     *
     * @param key    业务键
     * @param type   目标类型
     * @param loader 数据加载器（当缓存未命中时调用）
     * @param ttl    过期时间（若为 null 使用默认）
     * @param <T>    类型
     * @return 缓存值或加载的值
     */
    public <T> T getOrLoadWithLock(String key, Class<T> type, Supplier<T> loader, Duration ttl) {
        String fullKey = buildFullKey(key);
        
        // 1. 先尝试从缓存获取
        Optional<T> cached = get(fullKey, type);
        if (cached.isPresent()) {
            return cached.get();
        }

        // 2. 布隆过滤器快速过滤（如果启用）
        if (cacheConfig.bloomFilter().enabled() && !bloomFilter.mightContain(key, key)) {
            LOG.debugv("Bloom filter: key definitely not exists: {0}", key);
            // 直接加载并返回，不使用锁
            T value = loader.get();
            put(fullKey, value, ttl);
            return value;
        }

        // 3. 缓存未命中，使用分布式锁保护加载过程
        String lockKey = DistributedConstants.KeyPrefix.CACHE_BREAKDOWN_LOCK + key;
        
        Optional<LockContext> lockContextOpt = lockProvider.tryLock(lockKey);
        if (lockContextOpt.isEmpty()) {
            // 获取锁失败，再次尝试从缓存获取（可能其他线程已加载完成）
            LOG.debugv("Failed to acquire lock, retry cache get: {0}", key);
            Optional<T> retryCache = get(fullKey, type);
            if (retryCache.isPresent()) {
                return retryCache.get();
            }
            // 仍未命中，降级直接加载
            LOG.warnv("Lock timeout, fallback to direct load: {0}", key);
            T value = loader.get();
            put(fullKey, value, ttl);
            return value;
        }

        LockContext lockContext = lockContextOpt.get();
        try {
            // 4. 双重检查：获取锁后再次检查缓存
            Optional<T> doubleCheck = get(fullKey, type);
            if (doubleCheck.isPresent()) {
                LOG.debugv("Cache hit after acquiring lock (double check): {0}", key);
                return doubleCheck.get();
            }

            // 5. 执行加载
            LOG.debugv("Loading data with lock protection: {0}", key);
            T value = loader.get();
            
            // 6. 写入缓存
            put(fullKey, value, ttl);
            
            return value;

        } finally {
            // 7. 释放锁
            lockProvider.unlock(lockContext);
        }
    }

    /**
     * 带分布式锁的缓存读取或加载（使用默认TTL）
     *
     * @param key    业务键
     * @param type   目标类型
     * @param loader 数据加载器
     * @param <T>    类型
     * @return 缓存值或加载的值
     */
    public <T> T getOrLoadWithLock(String key, Class<T> type, Supplier<T> loader) {
        return getOrLoadWithLock(key, type, loader, null);
    }

    // ===========================
    // 辅助方法
    // ===========================

    private String buildFullKey(String key) {
        // 统一使用全局前缀，业务方只需要提供业务关键字
        return CacheConstants.KeyPrefix.GLOBAL + key;
    }

    private void logOperation(CacheOperationType type, String key) {
        if (LOG.isDebugEnabled()) {
            LOG.debugv("CacheFacade {0}: {1}", type.getDescription(), key);
        }
    }
}
