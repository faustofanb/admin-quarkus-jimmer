package io.github.faustofan.admin.shared.avaliable.ratelimit;

import io.github.faustofan.admin.shared.avaliable.config.AvailabilityConfig;
import io.github.faustofan.admin.shared.avaliable.constants.AvailabilityConstants;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分布式限流器实现
 * <p>
 * 基于Redis实现的分布式限流器，支持滑动窗口算法
 */
@ApplicationScoped
public class DistributedRateLimiter implements RateLimiter {

    private static final Logger LOG = Logger.getLogger(DistributedRateLimiter.class);

    private final AvailabilityConfig config;
    private final ReactiveRedisDataSource redisDataSource;
    private final ReactiveValueCommands<String, Long> valueCommands;
    private final Map<String, RateLimitRule> rules = new ConcurrentHashMap<>();

    @Inject
    public DistributedRateLimiter(AvailabilityConfig config, ReactiveRedisDataSource redisDataSource) {
        this.config = config;
        this.redisDataSource = redisDataSource;
        this.valueCommands = redisDataSource.value(Long.class);
    }

    @Override
    public boolean tryAcquire(String resourceName) {
        return tryAcquire(resourceName, 1);
    }

    @Override
    public boolean tryAcquire(String resourceName, int permits) {
        if (!config.rateLimit().enabled() || !config.rateLimit().distributed()) {
            return true;
        }

        try {
            return tryAcquireAsync(resourceName, permits)
                    .await().atMost(Duration.ofSeconds(1));
        } catch (Exception e) {
            LOG.warnf("%s Failed to check rate limit for %s: %s, allowing request",
                    AvailabilityConstants.LogPrefix.RATE_LIMIT, resourceName, e.getMessage());
            return true; // 失败时放行，避免影响正常业务
        }
    }

    private Uni<Boolean> tryAcquireAsync(String resourceName, int permits) {
        RateLimitRule rule = getOrCreateRule(resourceName);
        String key = buildKey(resourceName);
        long windowMs = rule.windowMs;

        return valueCommands.incr(key)
                .flatMap(count -> {
                    if (count == 1) {
                        // 首次请求，设置过期时间
                        return redisDataSource.key().expire(key, Duration.ofMillis(windowMs))
                                .replaceWith(count);
                    }
                    return Uni.createFrom().item(count);
                })
                .map(count -> {
                    boolean allowed = count <= rule.maxPermits;
                    if (!allowed) {
                        LOG.debugf("%s Rate limit exceeded for resource: %s, count: %d, max: %d",
                                AvailabilityConstants.LogPrefix.RATE_LIMIT, resourceName, count, rule.maxPermits);
                    }
                    return allowed;
                });
    }

    @Override
    public boolean tryAcquire(String resourceName, Duration timeout) {
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
        try {
            RateLimitRule rule = rules.get(resourceName);
            if (rule == null) {
                return Optional.empty();
            }

            String key = buildKey(resourceName);
            Long count = valueCommands.get(key)
                    .await().atMost(Duration.ofSeconds(1));

            if (count == null) {
                return Optional.of((long) rule.maxPermits);
            }
            return Optional.of(Math.max(0, rule.maxPermits - count));
        } catch (Exception e) {
            LOG.warnf("%s Failed to get available permits for %s: %s",
                    AvailabilityConstants.LogPrefix.RATE_LIMIT, resourceName, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<Long> getResetTimeMs(String resourceName) {
        try {
            String key = buildKey(resourceName);
            Long ttlSeconds = redisDataSource.key().ttl(key)
                    .await().atMost(Duration.ofSeconds(1));

            if (ttlSeconds == null || ttlSeconds < 0) {
                return Optional.empty();
            }
            return Optional.of(ttlSeconds * 1000); // 转换为毫秒
        } catch (Exception e) {
            LOG.warnf("%s Failed to get reset time for %s: %s",
                    AvailabilityConstants.LogPrefix.RATE_LIMIT, resourceName, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void configure(String resourceName, int permitsPerPeriod, Duration period) {
        rules.put(resourceName, new RateLimitRule(permitsPerPeriod, period.toMillis()));
        LOG.infof("%s Configured distributed rate limit for %s: %d permits per %s",
                AvailabilityConstants.LogPrefix.RATE_LIMIT, resourceName, permitsPerPeriod, period);
    }

    @Override
    public void remove(String resourceName) {
        rules.remove(resourceName);
        String key = buildKey(resourceName);
        try {
            redisDataSource.key().del(key)
                    .await().atMost(Duration.ofSeconds(1));
        } catch (Exception e) {
            LOG.warnf("%s Failed to remove rate limit key for %s: %s",
                    AvailabilityConstants.LogPrefix.RATE_LIMIT, resourceName, e.getMessage());
        }
    }

    @Override
    public void reset(String resourceName) {
        String key = buildKey(resourceName);
        try {
            redisDataSource.key().del(key)
                    .await().atMost(Duration.ofSeconds(1));
        } catch (Exception e) {
            LOG.warnf("%s Failed to reset rate limit for %s: %s",
                    AvailabilityConstants.LogPrefix.RATE_LIMIT, resourceName, e.getMessage());
        }
    }

    @Override
    public void resetAll() {
        rules.keySet().forEach(this::reset);
    }

    private String buildKey(String resourceName) {
        return AvailabilityConstants.KeyPrefix.RATE_LIMIT_COUNTER + resourceName;
    }

    private RateLimitRule getOrCreateRule(String resourceName) {
        return rules.computeIfAbsent(resourceName, k ->
                new RateLimitRule(
                        config.rateLimit().defaultPermits(),
                        config.rateLimit().defaultWindow().toMillis()
                )
        );
    }

    private record RateLimitRule(int maxPermits, long windowMs) {}
}
