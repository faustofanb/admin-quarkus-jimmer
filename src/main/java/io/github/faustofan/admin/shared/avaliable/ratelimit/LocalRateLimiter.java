package io.github.faustofan.admin.shared.avaliable.ratelimit;

import io.github.faustofan.admin.shared.avaliable.config.AvailabilityConfig;
import io.github.faustofan.admin.shared.avaliable.constants.AvailabilityConstants;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 本地限流器实现
 * <p>
 * 基于滑动窗口算法实现的本地JVM限流器
 */
@ApplicationScoped
public class LocalRateLimiter implements RateLimiter {

    private static final Logger LOG = Logger.getLogger(LocalRateLimiter.class);

    private final AvailabilityConfig config;
    private final Map<String, RateLimitBucket> buckets = new ConcurrentHashMap<>();

    @Inject
    public LocalRateLimiter(AvailabilityConfig config) {
        this.config = config;
    }

    @Override
    public boolean tryAcquire(String resourceName) {
        return tryAcquire(resourceName, 1);
    }

    @Override
    public boolean tryAcquire(String resourceName, int permits) {
        if (!config.rateLimit().enabled()) {
            return true;
        }

        RateLimitBucket bucket = getOrCreateBucket(resourceName);
        boolean acquired = bucket.tryAcquire(permits);

        if (!acquired) {
            LOG.debugf("%s Rate limit exceeded for resource: %s",
                    AvailabilityConstants.LogPrefix.RATE_LIMIT, resourceName);
        }

        return acquired;
    }

    @Override
    public boolean tryAcquire(String resourceName, Duration timeout) {
        if (!config.rateLimit().enabled()) {
            return true;
        }

        long startTime = System.currentTimeMillis();
        long timeoutMs = timeout.toMillis();

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (tryAcquire(resourceName)) {
                return true;
            }
            try {
                Thread.sleep(Math.min(10, timeoutMs / 10));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    @Override
    public void acquire(String resourceName) {
        acquire(resourceName, 1);
    }

    @Override
    public void acquire(String resourceName, int permits) {
        while (!tryAcquire(resourceName, permits)) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for rate limit permit", e);
            }
        }
    }

    @Override
    public Optional<Long> getAvailablePermits(String resourceName) {
        RateLimitBucket bucket = buckets.get(resourceName);
        if (bucket == null) {
            return Optional.empty();
        }
        return Optional.of(bucket.getAvailablePermits());
    }

    @Override
    public Optional<Long> getResetTimeMs(String resourceName) {
        RateLimitBucket bucket = buckets.get(resourceName);
        if (bucket == null) {
            return Optional.empty();
        }
        return Optional.of(bucket.getResetTimeMs());
    }

    @Override
    public void configure(String resourceName, int permitsPerPeriod, Duration period) {
        buckets.put(resourceName, new RateLimitBucket(permitsPerPeriod, period.toMillis()));
        LOG.infof("%s Configured rate limit for %s: %d permits per %s",
                AvailabilityConstants.LogPrefix.RATE_LIMIT, resourceName, permitsPerPeriod, period);
    }

    @Override
    public void remove(String resourceName) {
        buckets.remove(resourceName);
    }

    @Override
    public void reset(String resourceName) {
        RateLimitBucket bucket = buckets.get(resourceName);
        if (bucket != null) {
            bucket.reset();
        }
    }

    @Override
    public void resetAll() {
        buckets.values().forEach(RateLimitBucket::reset);
    }

    private RateLimitBucket getOrCreateBucket(String resourceName) {
        return buckets.computeIfAbsent(resourceName, k ->
                new RateLimitBucket(
                        config.rateLimit().defaultPermits(),
                        config.rateLimit().defaultWindow().toMillis()
                )
        );
    }

    /**
     * 限流桶实现（滑动窗口）
     */
    private static class RateLimitBucket {
        private final int maxPermits;
        private final long windowMs;
        private final AtomicLong count;
        private volatile long windowStart;

        RateLimitBucket(int maxPermits, long windowMs) {
            this.maxPermits = maxPermits;
            this.windowMs = windowMs;
            this.count = new AtomicLong(0);
            this.windowStart = System.currentTimeMillis();
        }

        synchronized boolean tryAcquire(int permits) {
            long now = System.currentTimeMillis();

            // 检查是否需要重置窗口
            if (now - windowStart >= windowMs) {
                windowStart = now;
                count.set(0);
            }

            // 检查是否可以获取许可
            if (count.get() + permits <= maxPermits) {
                count.addAndGet(permits);
                return true;
            }

            return false;
        }

        long getAvailablePermits() {
            long now = System.currentTimeMillis();
            if (now - windowStart >= windowMs) {
                return maxPermits;
            }
            return Math.max(0, maxPermits - count.get());
        }

        long getResetTimeMs() {
            long now = System.currentTimeMillis();
            long elapsed = now - windowStart;
            return Math.max(0, windowMs - elapsed);
        }

        void reset() {
            windowStart = System.currentTimeMillis();
            count.set(0);
        }
    }
}
