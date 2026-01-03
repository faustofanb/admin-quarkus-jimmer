package io.github.faustofan.admin.shared.distributed.lock;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * 锁提供者接口
 * <p>
 * 定义获取锁、释放锁、执行带锁操作等统一API
 */
public interface LockProvider {

    /**
     * 尝试获取锁（使用默认等待时间和持有时间）
     *
     * @param lockKey 锁的唯一标识
     * @return 锁上下文，如果获取成功；否则返回 empty
     */
    Optional<LockContext> tryLock(String lockKey);

    /**
     * 尝试获取锁（自定义等待时间和持有时间）
     *
     * @param lockKey   锁的唯一标识
     * @param waitTime  等待获取锁的最大时间
     * @param leaseTime 锁的持有时间（租约时间）
     * @return 锁上下文，如果获取成功；否则返回 empty
     */
    Optional<LockContext> tryLock(String lockKey, Duration waitTime, Duration leaseTime);

    /**
     * 释放锁
     *
     * @param context 锁上下文
     * @return true表示释放成功
     */
    boolean unlock(LockContext context);

    /**
     * 在锁的保护下执行操作（无返回值）
     * <p>
     * 自动获取锁，执行完成后自动释放锁
     *
     * @param lockKey  锁的唯一标识
     * @param runnable 要执行的操作
     * @return true表示执行成功（包括获取锁和执行操作）
     */
    boolean executeWithLock(String lockKey, Runnable runnable);

    /**
     * 在锁的保护下执行操作（无返回值，自定义等待时间）
     *
     * @param lockKey   锁的唯一标识
     * @param waitTime  等待获取锁的最大时间
     * @param leaseTime 锁的持有时间
     * @param runnable  要执行的操作
     * @return true表示执行成功
     */
    boolean executeWithLock(String lockKey, Duration waitTime, Duration leaseTime, Runnable runnable);

    /**
     * 在锁的保护下执行操作（有返回值）
     *
     * @param lockKey  锁的唯一标识
     * @param supplier 要执行的操作
     * @param <T>      返回值类型
     * @return 操作结果，如果获取锁失败则返回 empty
     */
    <T> Optional<T> executeWithLock(String lockKey, Supplier<T> supplier);

    /**
     * 在锁的保护下执行操作（有返回值，自定义等待时间）
     *
     * @param lockKey   锁的唯一标识
     * @param waitTime  等待获取锁的最大时间
     * @param leaseTime 锁的持有时间
     * @param supplier  要执行的操作
     * @param <T>       返回值类型
     * @return 操作结果，如果获取锁失败则返回 empty
     */
    <T> Optional<T> executeWithLock(String lockKey, Duration waitTime, Duration leaseTime, Supplier<T> supplier);

    /**
     * 在锁的保护下执行可能抛出异常的操作
     *
     * @param lockKey  锁的唯一标识
     * @param callable 要执行的操作
     * @param <T>      返回值类型
     * @return 操作结果，如果获取锁失败则返回 empty
     * @throws Exception 操作抛出的异常
     */
    <T> Optional<T> executeWithLock(String lockKey, Callable<T> callable) throws Exception;

    /**
     * 检查锁是否被持有
     *
     * @param lockKey 锁的唯一标识
     * @return true表示锁当前被持有
     */
    boolean isLocked(String lockKey);

    /**
     * 检查当前线程是否持有锁
     *
     * @param lockKey 锁的唯一标识
     * @return true表示当前线程持有锁
     */
    boolean isHeldByCurrentThread(String lockKey);

    /**
     * 强制释放锁（管理操作，慎用）
     *
     * @param lockKey 锁的唯一标识
     * @return true表示释放成功
     */
    boolean forceUnlock(String lockKey);
}
