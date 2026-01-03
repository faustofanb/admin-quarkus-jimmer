package io.github.faustofan.admin.shared.cache.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.faustofan.admin.shared.cache.config.CacheConfig;
import io.github.faustofan.admin.shared.cache.constants.CacheConstants;
import io.github.faustofan.admin.shared.cache.constants.CacheOperationType;
import io.github.faustofan.admin.shared.cache.exception.CacheException;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.string.StringCommands;
import io.quarkus.redis.datasource.string.SetArgs;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Redis 缓存管理器
 * <p>
 * 基于 Quarkus Redis Client 的缓存实现，作为二级缓存的L2层。
 * <p>
 * 特性：
 * <ul>
 *   <li>分布式共享缓存</li>
 *   <li>支持TTL过期</li>
 *   <li>防止缓存穿透（空值缓存）</li>
 *   <li>防止缓存雪崩（TTL随机偏移）</li>
 *   <li>防止缓存击穿（布隆过滤器+分布式锁）</li>
 *   <li>支持JSON序列化</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 写入缓存
 * redisCacheManager.set("user:1", user, Duration.ofHours(1));
 *
 * // 读取缓存
 * Optional<User> user = redisCacheManager.get("user:1", User.class);
 *
 * // 获取或加载
 * User user = redisCacheManager.getOrLoad(
 *     "user:1",
 *     () -> userRepository.findById(1L),
 *     Duration.ofHours(1)
 * );
 *
 * // 删除缓存
 * redisCacheManager.delete("user:1");
 *
 * // 批量删除
 * redisCacheManager.deleteByPattern("user:*");
 * }</pre>
 */
@ApplicationScoped
public class RedisCacheManager {

    private static final Logger LOG = Logger.getLogger(RedisCacheManager.class);

    private final RedisDataSource redisDataSource;
    private final StringCommands<String, String> stringCommands;
    private final KeyCommands<String> keyCommands;
    private final ObjectMapper objectMapper;
    private final CacheConfig cacheConfig;
    private final RedisBloomFilter bloomFilter;

    @Inject
    public RedisCacheManager(
            RedisDataSource redisDataSource,
            ObjectMapper objectMapper,
            CacheConfig cacheConfig,
            RedisBloomFilter bloomFilter) {
        this.redisDataSource = redisDataSource;
        this.stringCommands = redisDataSource.string(String.class);
        this.keyCommands = redisDataSource.key(String.class);
        this.objectMapper = objectMapper;
        this.cacheConfig = cacheConfig;
        this.bloomFilter = bloomFilter;
    }

    // ===========================
    // 读取操作
    // ===========================

    /**
     * 根据Key获取缓存值
     *
     * @param key  缓存Key
     * @param type 值类型
     * @param <T>  值类型
     * @return 缓存值（如果存在）
     */
    public <T> Optional<T> get(String key, Class<T> type) {
        if (!isEnabled() || key == null) {
            return Optional.empty();
        }

        try {
            String fullKey = buildKey(key);
            String value = stringCommands.get(fullKey);
            
            if (value == null) {
                logOperation(CacheOperationType.MISS, fullKey);
                return Optional.empty();
            }

            // 检查是否是空值占位符
            if (CacheConstants.NULL_PLACEHOLDER_VALUE.equals(value)) {
                logOperation(CacheOperationType.L2_HIT, fullKey + " (null placeholder)");
                return Optional.empty();
            }

            T result = deserialize(value, type);
            logOperation(CacheOperationType.L2_HIT, fullKey);
            return Optional.ofNullable(result);

        } catch (Exception e) {
            LOG.warnv(e, "Failed to get from Redis cache: {0}", key);
            return Optional.empty();
        }
    }

    /**
     * 根据Key获取缓存值（支持泛型）
     */
    public <T> Optional<T> get(String key, TypeReference<T> typeRef) {
        if (!isEnabled() || key == null) {
            return Optional.empty();
        }

        try {
            String fullKey = buildKey(key);
            String value = stringCommands.get(fullKey);
            
            if (value == null) {
                logOperation(CacheOperationType.MISS, fullKey);
                return Optional.empty();
            }

            if (CacheConstants.NULL_PLACEHOLDER_VALUE.equals(value)) {
                logOperation(CacheOperationType.L2_HIT, fullKey + " (null placeholder)");
                return Optional.empty();
            }

            T result = objectMapper.readValue(value, typeRef);
            logOperation(CacheOperationType.L2_HIT, fullKey);
            return Optional.ofNullable(result);

        } catch (Exception e) {
            LOG.warnv(e, "Failed to get from Redis cache: {0}", key);
            return Optional.empty();
        }
    }

    /**
     * 获取缓存值，如果不存在则通过loader加载并缓存
     *
     * @param key    缓存Key
     * @param loader 数据加载器
     * @param ttl    过期时间
     * @param <T>    值类型
     * @return 缓存值或加载的值
     */
    public <T> T getOrLoad(String key, java.util.function.Supplier<T> loader, Duration ttl) {
        if (!isEnabled()) {
            return loader.get();
        }

        String fullKey = buildKey(key);

        try {
            // 先尝试从缓存获取
            String cached = stringCommands.get(fullKey);
            if (cached != null) {
                if (CacheConstants.NULL_PLACEHOLDER_VALUE.equals(cached)) {
                    logOperation(CacheOperationType.L2_HIT, fullKey + " (null placeholder)");
                    return null;
                }
                @SuppressWarnings("unchecked")
                T result = (T) objectMapper.readValue(cached, Object.class);
                logOperation(CacheOperationType.L2_HIT, fullKey);
                return result;
            }

            // 缓存未命中，从loader加载
            logOperation(CacheOperationType.MISS, fullKey);
            T value = loader.get();

            // 写入缓存（包括null值）
            if (value == null) {
                setNull(key, cacheConfig.nullValueTtl());
            } else {
                set(key, value, ttl);
            }

            return value;

        } catch (Exception e) {
            LOG.warnv(e, "Failed to get or load from Redis, falling back to loader: {0}", key);
            return loader.get();
        }
    }

    // ===========================
    // 写入操作
    // ===========================

    /**
     * 写入缓存（使用默认TTL）
     *
     * @param key   缓存Key
     * @param value 缓存值
     * @param <T>   值类型
     */
    public <T> void set(String key, T value) {
        set(key, value, cacheConfig.defaultTtl());
    }

    /**
     * 写入缓存（指定TTL）
     *
     * @param key   缓存Key
     * @param value 缓存值
     * @param ttl   过期时间
     * @param <T>   值类型
     */
    public <T> void set(String key, T value, Duration ttl) {
        if (!isEnabled() || key == null || value == null) {
            return;
        }

        try {
            String fullKey = buildKey(key);
            String serialized = serialize(value);
            
            // 应用TTL随机偏移，防止缓存雪崩
            Duration finalTtl = applyTtlJitter(ttl);
            
            SetArgs args = new SetArgs().ex(finalTtl);
            stringCommands.set(fullKey, serialized, args);
            
            logOperation(CacheOperationType.PUT, fullKey);

        } catch (Exception e) {
            LOG.warnv(e, "Failed to set to Redis cache: {0}", key);
            throw CacheException.writeFailed(key, e);
        }
    }

    /**
     * 写入空值占位符（用于防止缓存穿透）
     *
     * @param key 缓存Key
     * @param ttl 过期时间
     */
    public void setNull(String key, Duration ttl) {
        if (!isEnabled() || key == null) {
            return;
        }

        try {
            String fullKey = buildKey(key);
            SetArgs args = new SetArgs().ex(ttl);
            stringCommands.set(fullKey, CacheConstants.NULL_PLACEHOLDER_VALUE, args);
            
            logOperation(CacheOperationType.PUT, fullKey + " (null placeholder)");

        } catch (Exception e) {
            LOG.warnv(e, "Failed to set null placeholder to Redis: {0}", key);
        }
    }

    /**
     * 如果Key不存在则写入（SET NX）
     *
     * @param key   缓存Key
     * @param value 缓存值
     * @param ttl   过期时间
     * @param <T>   值类型
     * @return true表示写入成功，false表示Key已存在
     */
    public <T> boolean setIfAbsent(String key, T value, Duration ttl) {
        if (!isEnabled() || key == null || value == null) {
            return false;
        }

        try {
            String fullKey = buildKey(key);
            String serialized = serialize(value);
            
            SetArgs args = new SetArgs().ex(ttl).nx();
            Boolean success = stringCommands.setnx(fullKey, serialized);
            
            if (Boolean.TRUE.equals(success)) {
                logOperation(CacheOperationType.PUT, fullKey);
                return true;
            }
            return false;

        } catch (Exception e) {
            LOG.warnv(e, "Failed to setIfAbsent to Redis: {0}", key);
            return false;
        }
    }

    // ===========================
    // 删除操作
    // ===========================

    /**
     * 删除缓存
     *
     * @param key 缓存Key
     */
    public void delete(String key) {
        if (!isEnabled() || key == null) {
            return;
        }

        try {
            String fullKey = buildKey(key);
            keyCommands.del(fullKey);
            logOperation(CacheOperationType.DELETE, fullKey);

        } catch (Exception e) {
            LOG.warnv(e, "Failed to delete from Redis: {0}", key);
            throw CacheException.deleteFailed(key, e);
        }
    }

    /**
     * 批量删除缓存
     *
     * @param keys 缓存Key列表
     */
    public void delete(String... keys) {
        if (!isEnabled() || keys == null || keys.length == 0) {
            return;
        }

        try {
            String[] fullKeys = Arrays.stream(keys)
                    .map(this::buildKey)
                    .toArray(String[]::new);
            keyCommands.del(fullKeys);
            logOperation(CacheOperationType.DELETE_BATCH, String.join(", ", fullKeys));

        } catch (Exception e) {
            LOG.warnv(e, "Failed to batch delete from Redis");
            throw CacheException.deleteFailed(Arrays.toString(keys), e);
        }
    }

    /**
     * 根据模式删除缓存
     *
     * @param pattern 匹配模式（如 "user:*"）
     */
    public void deleteByPattern(String pattern) {
        if (!isEnabled() || pattern == null) {
            return;
        }

        try {
            String fullPattern = buildKey(pattern);
            List<String> keys = keyCommands.keys(fullPattern);
            
            if (!keys.isEmpty()) {
                keyCommands.del(keys.toArray(new String[0]));
                logOperation(CacheOperationType.DELETE_BATCH, fullPattern + " (" + keys.size() + " keys)");
            }

        } catch (Exception e) {
            LOG.warnv(e, "Failed to delete by pattern from Redis: {0}", pattern);
        }
    }

    // ===========================
    // 存在性检查
    // ===========================

    /**
     * 检查Key是否存在
     *
     * @param key 缓存Key
     * @return true表示存在
     */
    public boolean exists(String key) {
        if (!isEnabled() || key == null) {
            return false;
        }

        try {
            String fullKey = buildKey(key);
            boolean exists = keyCommands.exists(fullKey);
            logOperation(CacheOperationType.EXISTS, fullKey);
            return exists;

        } catch (Exception e) {
            LOG.warnv(e, "Failed to check existence in Redis: {0}", key);
            return false;
        }
    }

    /**
     * 获取Key的过期时间（秒）
     *
     * @param key 缓存Key
     * @return 过期时间，-1表示永不过期，-2表示Key不存在
     */
    public long ttl(String key) {
        if (!isEnabled() || key == null) {
            return -2;
        }

        try {
            String fullKey = buildKey(key);
            return keyCommands.ttl(fullKey);

        } catch (Exception e) {
            LOG.warnv(e, "Failed to get TTL from Redis: {0}", key);
            return -2;
        }
    }

    /**
     * 设置Key的过期时间
     *
     * @param key 缓存Key
     * @param ttl 过期时间
     */
    public void expire(String key, Duration ttl) {
        if (!isEnabled() || key == null) {
            return;
        }

        try {
            String fullKey = buildKey(key);
            keyCommands.expire(fullKey, ttl.getSeconds());

        } catch (Exception e) {
            LOG.warnv(e, "Failed to set expiration in Redis: {0}", key);
        }
    }

    // ===========================
    // 辅助方法
    // ===========================

    /**
     * 构建完整的Redis Key
     */
    private String buildKey(String key) {
        String prefix = cacheConfig.redis().keyPrefix();
        return prefix + key;
    }

    /**
     * 应用TTL随机偏移（防止缓存雪崩）
     */
    private Duration applyTtlJitter(Duration ttl) {
        if (!cacheConfig.ttlJitterEnabled()) {
            return ttl;
        }

        long maxJitterSeconds = cacheConfig.maxTtlJitter().getSeconds();
        if (maxJitterSeconds <= 0) {
            return ttl;
        }

        long jitterSeconds = ThreadLocalRandom.current().nextLong(0, maxJitterSeconds);
        return ttl.plusSeconds(jitterSeconds);
    }

    /**
     * 序列化对象为JSON字符串
     */
    private <T> String serialize(T value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw CacheException.serializationFailed(value.getClass().getName(), e);
        }
    }

    /**
     * 从JSON字符串反序列化对象
     */
    private <T> T deserialize(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (Exception e) {
            throw CacheException.deserializationFailed(type.getName(), e);
        }
    }

    /**
     * 检查Redis缓存是否启用
     */
    public boolean isEnabled() {
        return cacheConfig.enabled() && cacheConfig.redis().enabled();
    }

    private void logOperation(CacheOperationType type, String key) {
        if (LOG.isDebugEnabled()) {
            LOG.debugv("Redis cache {0}: {1}", type.getDescription(), key);
        }
    }
}
