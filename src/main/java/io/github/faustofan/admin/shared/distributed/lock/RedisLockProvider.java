package io.github.faustofan.admin.shared.distributed.lock;

import io.github.faustofan.admin.shared.distributed.config.DistributedConfig;
import io.github.faustofan.admin.shared.distributed.constants.DistributedConstants;
import io.github.faustofan.admin.shared.distributed.constants.LockType;
import io.github.faustofan.admin.shared.distributed.exception.DistributedException;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.SetArgs;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Redis 分布式锁提供者
 * <p>
 * 基于 Redis SET NX EX 实现的分布式锁。
 * <p>
 * 特性：
 * <ul>
 *   <li>分布式环境下的互斥</li>
 *   <li>支持等待超时和自动过期</li>
 *   <li>安全释放（仅持有者可释放）</li>
 *   <li>防止死锁（锁自动过期）</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 获取锁并执行操作
 * Optional<String> result = redisLockProvider.executeWithLock(
 *     "order:create:123",
 *     () -> orderService.createOrder(request)
 * );
 *
 * // 手动获取锁
 * Optional<LockContext> context = redisLockProvider.tryLock("user:update:1");
 * if (context.isPresent()) {
 *     try {
 *         // 执行操作
 *     } finally {
 *         redisLockProvider.unlock(context.get());
 *     }
 * }
 * }</pre>
 */
@ApplicationScoped
public class RedisLockProvider implements LockProvider {

    private static final Logger LOG = Logger.getLogger(RedisLockProvider.class);

    private final ValueCommands<String, String> valueCommands;
    private final KeyCommands<String> keyCommands;
    private final DistributedConfig config;

    /**
     * 本地缓存锁上下文，用于验证持有者身份
     */
    private final Map<String, LockContext> localContexts = new ConcurrentHashMap<>();

    @Inject
    public RedisLockProvider(RedisDataSource redisDataSource, DistributedConfig config) {
        this.valueCommands = redisDataSource.value(String.class, String.class);
        this.keyCommands = redisDataSource.key(String.class);
        this.config = config;
    }

    @Override
    public Optional<LockContext> tryLock(String lockKey) {
        return tryLock(lockKey, config.lock().waitTime(), config.lock().leaseTime());
    }

    @Override
    public Optional<LockContext> tryLock(String lockKey, Duration waitTime, Duration leaseTime) {
        String fullKey = buildLockKey(lockKey);
        String holderId = generateHolderId();
        Instant startTime = Instant.now();
        long waitMillis = waitTime.toMillis();
        long retryIntervalMs = config.lock().retryInterval().toMillis();

        while (true) {
            try {
                // 尝试获取锁：SET key value NX EX seconds
                SetArgs args = new SetArgs().nx().ex(leaseTime);
                valueCommands.set(fullKey, holderId, args);
                
                // 验证是否成功获取锁
                String currentHolder = valueCommands.get(fullKey);
                if (holderId.equals(currentHolder)) {
                    Instant now = Instant.now();
                    LockContext context = LockContext.builder()
                            .lockKey(lockKey)
                            .holderId(holderId)
                            .lockType(LockType.REDIS)
                            .acquiredAt(now)
                            .expiresAt(now.plus(leaseTime))
                            .build();
                    
                    localContexts.put(lockKey, context);
                    LOG.debugv("Redis lock acquired: {0} by {1}", lockKey, holderId);
                    return Optional.of(context);
                }

                // 锁已被其他持有者持有，检查是否超时
                long elapsed = Instant.now().toEpochMilli() - startTime.toEpochMilli();
                if (elapsed >= waitMillis) {
                    LOG.debugv("Failed to acquire Redis lock: {0} (timeout after {1}ms)", lockKey, elapsed);
                    return Optional.empty();
                }

                // 等待一段时间后重试
                Thread.sleep(retryIntervalMs);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warnv("Interrupted while waiting for Redis lock: {0}", lockKey);
                return Optional.empty();
            } catch (Exception e) {
                LOG.warnv(e, "Error acquiring Redis lock: {0}", lockKey);
                return Optional.empty();
            }
        }
    }

    @Override
    public boolean unlock(LockContext context) {
        if (context == null || !context.isValid()) {
            return false;
        }

        String lockKey = context.getLockKey();
        String fullKey = buildLockKey(lockKey);
        String holderId = context.getHolderId();

        try {
            // 验证持有者身份
            String currentHolder = valueCommands.get(fullKey);
            if (!holderId.equals(currentHolder)) {
                LOG.warnv("Cannot unlock - not held by this holder: {0} (expected={1}, actual={2})",
                        lockKey, holderId, currentHolder);
                return false;
            }

            // 释放锁
            keyCommands.del(fullKey);
            context.invalidate();
            localContexts.remove(lockKey);
            LOG.debugv("Redis lock released: {0}", lockKey);
            return true;

        } catch (Exception e) {
            LOG.warnv(e, "Error releasing Redis lock: {0}", lockKey);
            throw DistributedException.lockReleaseFailed(lockKey, e);
        }
    }

    @Override
    public boolean executeWithLock(String lockKey, Runnable runnable) {
        return executeWithLock(lockKey, config.lock().waitTime(), config.lock().leaseTime(), runnable);
    }

    @Override
    public boolean executeWithLock(String lockKey, Duration waitTime, Duration leaseTime, Runnable runnable) {
        Optional<LockContext> contextOpt = tryLock(lockKey, waitTime, leaseTime);
        if (contextOpt.isEmpty()) {
            return false;
        }

        LockContext context = contextOpt.get();
        try {
            runnable.run();
            return true;
        } finally {
            unlock(context);
        }
    }

    @Override
    public <T> Optional<T> executeWithLock(String lockKey, Supplier<T> supplier) {
        return executeWithLock(lockKey, config.lock().waitTime(), config.lock().leaseTime(), supplier);
    }

    @Override
    public <T> Optional<T> executeWithLock(String lockKey, Duration waitTime, Duration leaseTime, Supplier<T> supplier) {
        Optional<LockContext> contextOpt = tryLock(lockKey, waitTime, leaseTime);
        if (contextOpt.isEmpty()) {
            return Optional.empty();
        }

        LockContext context = contextOpt.get();
        try {
            return Optional.ofNullable(supplier.get());
        } finally {
            unlock(context);
        }
    }

    @Override
    public <T> Optional<T> executeWithLock(String lockKey, Callable<T> callable) throws Exception {
        Optional<LockContext> contextOpt = tryLock(lockKey);
        if (contextOpt.isEmpty()) {
            return Optional.empty();
        }

        LockContext context = contextOpt.get();
        try {
            return Optional.ofNullable(callable.call());
        } finally {
            unlock(context);
        }
    }

    @Override
    public boolean isLocked(String lockKey) {
        String fullKey = buildLockKey(lockKey);
        return keyCommands.exists(fullKey);
    }

    @Override
    public boolean isHeldByCurrentThread(String lockKey) {
        LockContext context = localContexts.get(lockKey);
        if (context == null || !context.isValid()) {
            return false;
        }

        String fullKey = buildLockKey(lockKey);
        String currentHolder = valueCommands.get(fullKey);
        return context.getHolderId().equals(currentHolder);
    }

    @Override
    public boolean forceUnlock(String lockKey) {
        String fullKey = buildLockKey(lockKey);
        try {
            keyCommands.del(fullKey);
            localContexts.remove(lockKey);
            LOG.warnv("Force unlocked Redis lock: {0}", lockKey);
            return true;
        } catch (Exception e) {
            LOG.warnv(e, "Error force unlocking Redis lock: {0}", lockKey);
            return false;
        }
    }

    // ===========================
    // 辅助方法
    // ===========================

    /**
     * 构建完整的锁Key
     */
    private String buildLockKey(String lockKey) {
        return DistributedConstants.KeyPrefix.LOCK + lockKey;
    }

    /**
     * 生成持有者ID
     * <p>
     * 格式：线程ID:UUID片段
     */
    private String generateHolderId() {
        return Thread.currentThread().getId() + ":" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 获取锁类型
     */
    public LockType getLockType() {
        return LockType.REDIS;
    }
}
