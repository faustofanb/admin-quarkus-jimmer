package io.github.faustofan.admin.shared.distributed;

import io.github.faustofan.admin.shared.distributed.config.DistributedConfig;
import io.github.faustofan.admin.shared.distributed.constants.LockType;
import io.github.faustofan.admin.shared.distributed.id.IdGenerator;
import io.github.faustofan.admin.shared.distributed.id.SnowflakeIdGenerator;
import io.github.faustofan.admin.shared.distributed.idempotent.IdempotentChecker;
import io.github.faustofan.admin.shared.distributed.lock.LocalLockProvider;
import io.github.faustofan.admin.shared.distributed.lock.LockContext;
import io.github.faustofan.admin.shared.distributed.lock.LockProvider;
import io.github.faustofan.admin.shared.distributed.lock.RedisLockProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * 分布式基础设施统一门面
 * <p>
 * 对外统一暴露分布式锁、ID生成、幂等检查等API。
 * <p>
 * 特性：
 * <ul>
 *   <li>分布式锁（Redis）和本地锁（JVM）</li>
 *   <li>雪花算法ID生成</li>
 *   <li>幂等检查</li>
 *   <li>根据配置自动选择实现</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 注入 DistributedFacade
 * @Inject
 * DistributedFacade distributedFacade;
 *
 * // 使用分布式锁
 * Optional<User> user = distributedFacade.executeWithLock(
 *     "user:update:123",
 *     () -> userService.updateUser(request)
 * );
 *
 * // 生成分布式ID
 * long id = distributedFacade.nextId();
 * String idStr = distributedFacade.nextIdStr();
 *
 * // 幂等检查
 * if (distributedFacade.checkIdempotent("order:create:123")) {
 *     // 首次请求
 *     orderService.createOrder(request);
 * }
 *
 * // 缓存防击穿（带锁的缓存加载）
 * User user = distributedFacade.getOrLoadWithLock(
 *     "user:1",
 *     () -> userRepository.findById(1L)
 * );
 * }</pre>
 */
@ApplicationScoped
public class DistributedFacade {

    private static final Logger LOG = Logger.getLogger(DistributedFacade.class);

    private final LocalLockProvider localLockProvider;
    private final RedisLockProvider redisLockProvider;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final IdempotentChecker idempotentChecker;
    private final DistributedConfig config;

    @Inject
    public DistributedFacade(
            LocalLockProvider localLockProvider,
            RedisLockProvider redisLockProvider,
            SnowflakeIdGenerator snowflakeIdGenerator,
            IdempotentChecker idempotentChecker,
            DistributedConfig config) {
        this.localLockProvider = localLockProvider;
        this.redisLockProvider = redisLockProvider;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.idempotentChecker = idempotentChecker;
        this.config = config;
    }

    // ===========================
    // 分布式锁 API
    // ===========================

    /**
     * 获取默认锁提供者（根据配置）
     */
    public LockProvider getLockProvider() {
        LockType type = config.lock().type();
        return switch (type) {
            case LOCAL -> localLockProvider;
            case REDIS -> redisLockProvider;
            case AUTO -> config.enabled() ? redisLockProvider : localLockProvider;
        };
    }

    /**
     * 获取指定类型的锁提供者
     */
    public LockProvider getLockProvider(LockType type) {
        return switch (type) {
            case LOCAL -> localLockProvider;
            case REDIS -> redisLockProvider;
            case AUTO -> getLockProvider();
        };
    }

    /**
     * 尝试获取锁
     */
    public Optional<LockContext> tryLock(String lockKey) {
        return getLockProvider().tryLock(lockKey);
    }

    /**
     * 尝试获取锁（自定义等待时间和持有时间）
     */
    public Optional<LockContext> tryLock(String lockKey, Duration waitTime, Duration leaseTime) {
        return getLockProvider().tryLock(lockKey, waitTime, leaseTime);
    }

    /**
     * 释放锁
     */
    public boolean unlock(LockContext context) {
        if (context == null) {
            return false;
        }
        return getLockProvider(context.getLockType()).unlock(context);
    }

    /**
     * 在锁的保护下执行操作（无返回值）
     */
    public boolean executeWithLock(String lockKey, Runnable runnable) {
        return getLockProvider().executeWithLock(lockKey, runnable);
    }

    /**
     * 在锁的保护下执行操作（有返回值）
     */
    public <T> Optional<T> executeWithLock(String lockKey, Supplier<T> supplier) {
        return getLockProvider().executeWithLock(lockKey, supplier);
    }

    /**
     * 在锁的保护下执行操作（自定义等待时间和持有时间）
     */
    public <T> Optional<T> executeWithLock(String lockKey, Duration waitTime, Duration leaseTime, Supplier<T> supplier) {
        return getLockProvider().executeWithLock(lockKey, waitTime, leaseTime, supplier);
    }

    /**
     * 在锁的保护下执行可能抛出异常的操作
     */
    public <T> Optional<T> executeWithLock(String lockKey, Callable<T> callable) throws Exception {
        return getLockProvider().executeWithLock(lockKey, callable);
    }

    /**
     * 检查锁是否被持有
     */
    public boolean isLocked(String lockKey) {
        return getLockProvider().isLocked(lockKey);
    }

    // ===========================
    // ID 生成 API
    // ===========================

    /**
     * 生成下一个唯一ID
     */
    public long nextId() {
        return snowflakeIdGenerator.nextId();
    }

    /**
     * 生成下一个唯一ID（字符串形式）
     */
    public String nextIdStr() {
        return snowflakeIdGenerator.nextIdStr();
    }

    /**
     * 批量生成ID
     */
    public long[] nextIds(int count) {
        return snowflakeIdGenerator.nextIds(count);
    }

    /**
     * 获取ID生成器
     */
    public IdGenerator getIdGenerator() {
        return snowflakeIdGenerator;
    }

    /**
     * 解析ID的时间戳
     */
    public long parseIdTimestamp(long id) {
        return snowflakeIdGenerator.parseTimestamp(id);
    }

    // ===========================
    // 幂等检查 API
    // ===========================

    /**
     * 检查是否是首次请求
     */
    public boolean checkIdempotent(String idempotentKey) {
        return idempotentChecker.check(idempotentKey);
    }

    /**
     * 检查并标记请求
     */
    public boolean checkAndMarkIdempotent(String idempotentKey) {
        return idempotentChecker.checkAndMark(idempotentKey);
    }

    /**
     * 检查并标记请求（自定义TTL）
     */
    public boolean checkAndMarkIdempotent(String idempotentKey, Duration ttl) {
        return idempotentChecker.checkAndMark(idempotentKey, ttl);
    }

    /**
     * 执行幂等操作
     */
    public <T> T executeIfFirst(String idempotentKey, Supplier<T> supplier) {
        return idempotentChecker.executeIfFirst(idempotentKey, supplier);
    }

    /**
     * 移除幂等标记
     */
    public void removeIdempotent(String idempotentKey) {
        idempotentChecker.remove(idempotentKey);
    }

    /**
     * 生成幂等Token
     */
    public String generateIdempotentToken() {
        return idempotentChecker.generateToken();
    }

    /**
     * 获取幂等检查器
     */
    public IdempotentChecker getIdempotentChecker() {
        return idempotentChecker;
    }

    // ===========================
    // 缓存防击穿 API
    // ===========================

    /**
     * 带锁的缓存加载（防止缓存击穿）
     * <p>
     * 当缓存未命中时，使用分布式锁保护，只允许一个线程加载数据。
     *
     * @param lockKey 锁Key（通常与缓存Key相关）
     * @param loader  数据加载器
     * @param <T>     返回值类型
     * @return 加载的数据，如果获取锁失败返回 empty
     */
    public <T> Optional<T> loadWithLock(String lockKey, Supplier<T> loader) {
        return executeWithLock(lockKey, loader);
    }

    /**
     * 带锁的缓存加载（自定义等待时间）
     *
     * @param lockKey  锁Key
     * @param waitTime 等待时间
     * @param loader   数据加载器
     * @param <T>      返回值类型
     * @return 加载的数据
     */
    public <T> Optional<T> loadWithLock(String lockKey, Duration waitTime, Supplier<T> loader) {
        return executeWithLock(lockKey, waitTime, config.lock().leaseTime(), loader);
    }

    // ===========================
    // 配置 API
    // ===========================

    /**
     * 检查分布式功能是否启用
     */
    public boolean isEnabled() {
        return config.enabled();
    }

    /**
     * 获取配置
     */
    public DistributedConfig getConfig() {
        return config;
    }
}
