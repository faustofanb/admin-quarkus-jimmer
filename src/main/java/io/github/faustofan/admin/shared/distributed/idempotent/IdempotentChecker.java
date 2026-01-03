package io.github.faustofan.admin.shared.distributed.idempotent;

import io.github.faustofan.admin.shared.distributed.config.DistributedConfig;
import io.github.faustofan.admin.shared.distributed.constants.DistributedConstants;
import io.github.faustofan.admin.shared.distributed.exception.DistributedException;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.SetArgs;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 幂等检查器
 * <p>
 * 用于防止重复请求，支持本地和Redis存储。
 * <p>
 * 特性：
 * <ul>
 *   <li>支持Token、参数、请求头等多种幂等策略</li>
 *   <li>支持本地缓存和Redis存储</li>
 *   <li>自动过期清理</li>
 *   <li>支持自定义TTL</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 检查并标记请求
 * if (idempotentChecker.checkAndMark("order:create:123")) {
 *     // 首次请求，执行业务逻辑
 *     orderService.createOrder(request);
 * } else {
 *     // 重复请求，拒绝或返回之前的结果
 *     throw new DuplicateRequestException();
 * }
 *
 * // 使用包装方法
 * Order order = idempotentChecker.executeIfFirst(
 *     "order:create:123",
 *     () -> orderService.createOrder(request)
 * );
 * }</pre>
 */
@ApplicationScoped
public class IdempotentChecker {

    private static final Logger LOG = Logger.getLogger(IdempotentChecker.class);

    /**
     * 幂等标记值
     */
    private static final String IDEMPOTENT_MARKER = "1";

    private final ValueCommands<String, String> valueCommands;
    private final DistributedConfig config;

    /**
     * 本地幂等缓存（作为Redis的补充或降级）
     */
    private final Map<String, Long> localCache = new ConcurrentHashMap<>();

    @Inject
    public IdempotentChecker(RedisDataSource redisDataSource, DistributedConfig config) {
        this.valueCommands = redisDataSource.value(String.class, String.class);
        this.config = config;
    }

    /**
     * 检查是否是首次请求
     * <p>
     * 仅检查，不标记
     *
     * @param idempotentKey 幂等Key
     * @return true表示是首次请求，false表示重复请求
     */
    public boolean check(String idempotentKey) {
        if (!isEnabled() || idempotentKey == null || idempotentKey.isBlank()) {
            return true;
        }

        String fullKey = buildKey(idempotentKey);

        try {
            if (config.idempotent().useRedis()) {
                String value = valueCommands.get(fullKey);
                return value == null;
            } else {
                return !localCache.containsKey(fullKey) || isLocalExpired(fullKey);
            }
        } catch (Exception e) {
            LOG.warnv(e, "Error checking idempotent key: {0}", idempotentKey);
            // 出错时降级到本地检查
            return !localCache.containsKey(fullKey);
        }
    }

    /**
     * 检查并标记请求
     * <p>
     * 如果是首次请求，则标记并返回true；否则返回false
     *
     * @param idempotentKey 幂等Key
     * @return true表示是首次请求，false表示重复请求
     */
    public boolean checkAndMark(String idempotentKey) {
        return checkAndMark(idempotentKey, config.idempotent().ttl());
    }

    /**
     * 检查并标记请求（自定义TTL）
     *
     * @param idempotentKey 幂等Key
     * @param ttl           过期时间
     * @return true表示是首次请求，false表示重复请求
     */
    public boolean checkAndMark(String idempotentKey, Duration ttl) {
        if (!isEnabled() || idempotentKey == null || idempotentKey.isBlank()) {
            return true;
        }

        String fullKey = buildKey(idempotentKey);

        try {
            if (config.idempotent().useRedis()) {
                // 使用 SET NX EX 原子操作
                SetArgs args = new SetArgs().nx().ex(ttl);
                valueCommands.set(fullKey, IDEMPOTENT_MARKER, args);
                
                // 验证是否设置成功
                String value = valueCommands.get(fullKey);
                if (IDEMPOTENT_MARKER.equals(value)) {
                    LOG.debugv("Idempotent key marked: {0}", idempotentKey);
                    return true;
                }
                LOG.debugv("Duplicate request detected: {0}", idempotentKey);
                return false;
            } else {
                // 使用本地缓存
                return markLocal(fullKey, ttl);
            }
        } catch (Exception e) {
            LOG.warnv(e, "Error marking idempotent key: {0}", idempotentKey);
            // 出错时降级到本地缓存
            return markLocal(fullKey, ttl);
        }
    }

    /**
     * 执行幂等操作
     * <p>
     * 如果是首次请求，执行操作并返回结果；否则抛出异常
     *
     * @param idempotentKey 幂等Key
     * @param supplier      要执行的操作
     * @param <T>           返回值类型
     * @return 操作结果
     * @throws DistributedException 如果是重复请求
     */
    public <T> T executeIfFirst(String idempotentKey, Supplier<T> supplier) {
        return executeIfFirst(idempotentKey, config.idempotent().ttl(), supplier);
    }

    /**
     * 执行幂等操作（自定义TTL）
     *
     * @param idempotentKey 幂等Key
     * @param ttl           过期时间
     * @param supplier      要执行的操作
     * @param <T>           返回值类型
     * @return 操作结果
     * @throws DistributedException 如果是重复请求
     */
    public <T> T executeIfFirst(String idempotentKey, Duration ttl, Supplier<T> supplier) {
        if (!checkAndMark(idempotentKey, ttl)) {
            throw DistributedException.duplicateRequest(idempotentKey);
        }
        
        try {
            return supplier.get();
        } catch (Exception e) {
            // 执行失败时，移除幂等标记，允许重试
            remove(idempotentKey);
            throw e;
        }
    }

    /**
     * 移除幂等标记
     *
     * @param idempotentKey 幂等Key
     */
    public void remove(String idempotentKey) {
        if (idempotentKey == null || idempotentKey.isBlank()) {
            return;
        }

        String fullKey = buildKey(idempotentKey);

        try {
            if (config.idempotent().useRedis()) {
                valueCommands.getdel(fullKey);
            }
            localCache.remove(fullKey);
            LOG.debugv("Idempotent key removed: {0}", idempotentKey);
        } catch (Exception e) {
            LOG.warnv(e, "Error removing idempotent key: {0}", idempotentKey);
        }
    }

    /**
     * 生成Token（用于Token幂等策略）
     *
     * @return 唯一Token
     */
    public String generateToken() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 预设Token（用于Token幂等策略）
     *
     * @param token 预设的Token
     * @param ttl   过期时间
     * @return true表示设置成功
     */
    public boolean presetToken(String token, Duration ttl) {
        if (token == null || token.isBlank()) {
            return false;
        }

        String fullKey = buildKey(token);
        
        try {
            if (config.idempotent().useRedis()) {
                SetArgs args = new SetArgs().ex(ttl);
                valueCommands.set(fullKey, IDEMPOTENT_MARKER, args);
                return true;
            } else {
                return markLocal(fullKey, ttl);
            }
        } catch (Exception e) {
            LOG.warnv(e, "Error presetting token: {0}", token);
            return false;
        }
    }

    // ===========================
    // 辅助方法
    // ===========================

    private String buildKey(String key) {
        return DistributedConstants.KeyPrefix.IDEMPOTENT + key;
    }

    private boolean markLocal(String fullKey, Duration ttl) {
        Long existingExpiry = localCache.get(fullKey);
        if (existingExpiry != null && System.currentTimeMillis() < existingExpiry) {
            return false; // 重复请求
        }

        long expiryTime = System.currentTimeMillis() + ttl.toMillis();
        Long previous = localCache.putIfAbsent(fullKey, expiryTime);
        
        if (previous == null) {
            return true; // 首次标记成功
        }
        
        // 检查是否已过期
        if (System.currentTimeMillis() >= previous) {
            localCache.put(fullKey, expiryTime);
            return true;
        }
        
        return false; // 重复请求
    }

    private boolean isLocalExpired(String fullKey) {
        Long expiryTime = localCache.get(fullKey);
        if (expiryTime == null) {
            return true;
        }
        if (System.currentTimeMillis() >= expiryTime) {
            localCache.remove(fullKey);
            return true;
        }
        return false;
    }

    public boolean isEnabled() {
        return config.enabled() && config.idempotent().enabled();
    }
}
