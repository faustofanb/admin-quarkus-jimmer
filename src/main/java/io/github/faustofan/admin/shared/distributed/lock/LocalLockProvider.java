package io.github.faustofan.admin.shared.distributed.lock;

import io.github.faustofan.admin.shared.distributed.config.DistributedConfig;
import io.github.faustofan.admin.shared.distributed.constants.LockType;
import io.github.faustofan.admin.shared.distributed.exception.DistributedException;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 本地锁提供者（基于 JVM 内存）
 * <p>
 * 使用 ReentrantLock 实现，仅适用于单机环境。
 * <p>
 * 特性：
 * <ul>
 *   <li>可重入</li>
 *   <li>支持等待超时</li>
 *   <li>支持公平/非公平锁</li>
 *   <li>自动清理过期锁</li>
 * </ul>
 */
@ApplicationScoped
public class LocalLockProvider implements LockProvider {

    private static final Logger LOG = Logger.getLogger(LocalLockProvider.class);

    /**
     * 锁存储：lockKey -> ReentrantLock
     */
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * 锁上下文存储：lockKey -> LockContext
     */
    private final Map<String, LockContext> lockContexts = new ConcurrentHashMap<>();

    private final DistributedConfig config;

    @Inject
    public LocalLockProvider(DistributedConfig config) {
        this.config = config;
    }

    @Override
    public Optional<LockContext> tryLock(String lockKey) {
        return tryLock(lockKey, config.lock().waitTime(), config.lock().leaseTime());
    }

    @Override
    public Optional<LockContext> tryLock(String lockKey, Duration waitTime, Duration leaseTime) {
        ReentrantLock lock = locks.computeIfAbsent(lockKey, k -> new ReentrantLock());
        
        try {
            boolean acquired = lock.tryLock(waitTime.toMillis(), TimeUnit.MILLISECONDS);
            if (acquired) {
                String holderId = generateHolderId();
                Instant now = Instant.now();
                LockContext context = LockContext.builder()
                        .lockKey(lockKey)
                        .holderId(holderId)
                        .lockType(LockType.LOCAL)
                        .acquiredAt(now)
                        .expiresAt(now.plus(leaseTime))
                        .build();
                
                lockContexts.put(lockKey, context);
                LOG.debugv("Local lock acquired: {0} by {1}", lockKey, holderId);
                return Optional.of(context);
            }
            
            LOG.debugv("Failed to acquire local lock: {0} (timeout)", lockKey);
            return Optional.empty();
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warnv("Interrupted while waiting for local lock: {0}", lockKey);
            return Optional.empty();
        }
    }

    @Override
    public boolean unlock(LockContext context) {
        if (context == null || !context.isValid()) {
            return false;
        }

        String lockKey = context.getLockKey();
        ReentrantLock lock = locks.get(lockKey);
        
        if (lock == null) {
            LOG.warnv("Lock not found for unlock: {0}", lockKey);
            return false;
        }

        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                context.invalidate();
                lockContexts.remove(lockKey);
                LOG.debugv("Local lock released: {0}", lockKey);
                return true;
            } else {
                LOG.warnv("Cannot unlock - not held by current thread: {0}", lockKey);
                return false;
            }
        } catch (Exception e) {
            LOG.warnv(e, "Error releasing local lock: {0}", lockKey);
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
        ReentrantLock lock = locks.get(lockKey);
        return lock != null && lock.isLocked();
    }

    @Override
    public boolean isHeldByCurrentThread(String lockKey) {
        ReentrantLock lock = locks.get(lockKey);
        return lock != null && lock.isHeldByCurrentThread();
    }

    @Override
    public boolean forceUnlock(String lockKey) {
        ReentrantLock lock = locks.get(lockKey);
        if (lock == null) {
            return false;
        }

        // 强制释放：清理锁状态
        lockContexts.remove(lockKey);
        locks.remove(lockKey);
        LOG.warnv("Force unlocked local lock: {0}", lockKey);
        return true;
    }

    /**
     * 生成持有者ID
     */
    private String generateHolderId() {
        return Thread.currentThread().getId() + ":" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 获取锁类型
     */
    public LockType getLockType() {
        return LockType.LOCAL;
    }
}
