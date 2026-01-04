package io.github.faustofan.admin.shared.avaliable.bulkhead;

import io.github.faustofan.admin.shared.avaliable.config.AvailabilityConfig;
import io.github.faustofan.admin.shared.avaliable.constants.AvailabilityConstants;
import io.github.faustofan.admin.shared.avaliable.exception.BulkheadFullException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * 隔离舱管理器
 * <p>
 * 提供资源隔离能力，限制并发执行数量，防止级联故障
 */
@ApplicationScoped
public class BulkheadManager {

    private static final Logger LOG = Logger.getLogger(BulkheadManager.class);

    private final AvailabilityConfig config;
    private final Map<String, BulkheadSlot> bulkheads = new ConcurrentHashMap<>();

    @Inject
    public BulkheadManager(AvailabilityConfig config) {
        this.config = config;
    }

    /**
     * 执行带隔离保护的操作
     */
    public <T> T execute(String name, Supplier<T> supplier) {
        return execute(name, supplier, config.bulkhead().waitTimeout());
    }

    /**
     * 执行带隔离保护的操作（自定义等待超时）
     */
    public <T> T execute(String name, Supplier<T> supplier, Duration waitTimeout) {
        if (!config.bulkhead().enabled()) {
            return supplier.get();
        }

        BulkheadSlot slot = getOrCreateSlot(name);

        try {
            boolean acquired = slot.tryAcquire(waitTimeout);
            if (!acquired) {
                LOG.debugf("%s Bulkhead %s is full, rejecting request",
                        AvailabilityConstants.LogPrefix.BULKHEAD, name);
                throw new BulkheadFullException(name, slot.getMaxConcurrent(), slot.getQueueSize());
            }

            try {
                return supplier.get();
            } finally {
                slot.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for bulkhead slot", e);
        }
    }

    /**
     * 异步执行带隔离保护的操作
     */
    public <T> CompletableFuture<T> executeAsync(String name, Supplier<T> supplier) {
        return executeAsync(name, supplier, config.bulkhead().waitTimeout());
    }

    /**
     * 异步执行带隔离保护的操作（自定义等待超时）
     */
    public <T> CompletableFuture<T> executeAsync(String name, Supplier<T> supplier, Duration waitTimeout) {
        if (!config.bulkhead().enabled()) {
            return CompletableFuture.supplyAsync(supplier);
        }

        BulkheadSlot slot = getOrCreateSlot(name);

        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean acquired = slot.tryAcquire(waitTimeout);
                if (!acquired) {
                    throw new BulkheadFullException(name, slot.getMaxConcurrent(), slot.getQueueSize());
                }

                try {
                    return supplier.get();
                } finally {
                    slot.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for bulkhead slot", e);
            }
        });
    }

    /**
     * 尝试获取隔离舱槽位
     */
    public boolean tryAcquire(String name) {
        return tryAcquire(name, Duration.ZERO);
    }

    /**
     * 尝试获取隔离舱槽位（等待一段时间）
     */
    public boolean tryAcquire(String name, Duration waitTimeout) {
        if (!config.bulkhead().enabled()) {
            return true;
        }

        BulkheadSlot slot = getOrCreateSlot(name);
        try {
            return slot.tryAcquire(waitTimeout);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 释放隔离舱槽位
     */
    public void release(String name) {
        BulkheadSlot slot = bulkheads.get(name);
        if (slot != null) {
            slot.release();
        }
    }

    /**
     * 获取当前活跃执行数
     */
    public int getActiveCount(String name) {
        BulkheadSlot slot = bulkheads.get(name);
        return slot != null ? slot.getActiveCount() : 0;
    }

    /**
     * 获取可用槽位数
     */
    public int getAvailableSlots(String name) {
        BulkheadSlot slot = bulkheads.get(name);
        return slot != null ? slot.getAvailableSlots() : config.bulkhead().maxConcurrentCalls();
    }

    /**
     * 获取等待队列大小
     */
    public int getWaitingCount(String name) {
        BulkheadSlot slot = bulkheads.get(name);
        return slot != null ? slot.getWaitingCount() : 0;
    }

    /**
     * 配置隔离舱
     */
    public void configure(String name, int maxConcurrentCalls, int waitingTaskQueue) {
        bulkheads.put(name, new BulkheadSlot(maxConcurrentCalls, waitingTaskQueue));
        LOG.infof("%s Configured bulkhead %s: maxConcurrent=%d, queueSize=%d",
                AvailabilityConstants.LogPrefix.BULKHEAD, name, maxConcurrentCalls, waitingTaskQueue);
    }

    /**
     * 移除隔离舱
     */
    public void remove(String name) {
        bulkheads.remove(name);
    }

    /**
     * 重置隔离舱
     */
    public void reset(String name) {
        BulkheadSlot slot = bulkheads.get(name);
        if (slot != null) {
            slot.reset();
        }
    }

    private BulkheadSlot getOrCreateSlot(String name) {
        return bulkheads.computeIfAbsent(name, k ->
                new BulkheadSlot(
                        config.bulkhead().maxConcurrentCalls(),
                        config.bulkhead().waitingTaskQueue()
                )
        );
    }

    /**
     * 隔离舱槽位实现
     */
    private static class BulkheadSlot {
        private final Semaphore semaphore;
        private final int maxConcurrent;
        private final int queueSize;

        BulkheadSlot(int maxConcurrent, int queueSize) {
            this.maxConcurrent = maxConcurrent;
            this.queueSize = queueSize;
            this.semaphore = new Semaphore(maxConcurrent, true);
        }

        boolean tryAcquire(Duration timeout) throws InterruptedException {
            if (timeout.isZero() || timeout.isNegative()) {
                return semaphore.tryAcquire();
            }
            return semaphore.tryAcquire(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        void release() {
            semaphore.release();
        }

        int getActiveCount() {
            return maxConcurrent - semaphore.availablePermits();
        }

        int getAvailableSlots() {
            return semaphore.availablePermits();
        }

        int getWaitingCount() {
            return semaphore.getQueueLength();
        }

        int getMaxConcurrent() {
            return maxConcurrent;
        }

        int getQueueSize() {
            return queueSize;
        }

        void reset() {
            // 尝试释放所有已占用的许可
            int used = maxConcurrent - semaphore.availablePermits();
            if (used > 0) {
                semaphore.release(used);
            }
        }
    }
}
