package io.github.faustofan.admin.shared.cache.config;

import io.github.faustofan.admin.shared.cache.constants.CacheConstants;
import io.github.faustofan.admin.shared.cache.constants.CacheStrategy;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.time.Duration;
import java.util.Optional;

/**
 * 缓存配置映射接口
 * <p>
 * 使用 Quarkus ConfigMapping 进行类型安全的配置绑定
 * <p>
 * 配置示例 (application.yaml):
 * <pre>
 * app:
 *   cache:
 *     enabled: true
 *     default-strategy: TWO_LEVEL
 *     default-ttl: PT1H
 *     null-value-ttl: PT2M
 *     ttl-jitter-enabled: true
 *     max-ttl-jitter: PT5M
 *     bloom-filter:
 *       enabled: true
 *       expected-insertions: 1000000
 *       false-positive-rate: 0.01
 * </pre>
 */
@ConfigMapping(prefix = "app.cache")
public interface CacheConfig {

    /**
     * 是否启用缓存
     */
    @WithName("enabled")
    @WithDefault("true")
    boolean enabled();

    /**
     * 默认缓存策略
     */
    @WithName("default-strategy")
    @WithDefault("TWO_LEVEL")
    CacheStrategy defaultStrategy();

    /**
     * 默认缓存过期时间
     */
    @WithName("default-ttl")
    @WithDefault("PT1H")
    Duration defaultTtl();

    /**
     * 空值缓存过期时间（用于防止缓存穿透）
     */
    @WithName("null-value-ttl")
    @WithDefault("PT2M")
    Duration nullValueTtl();

    /**
     * 是否启用TTL随机偏移（用于防止缓存雪崩）
     */
    @WithName("ttl-jitter-enabled")
    @WithDefault("true")
    boolean ttlJitterEnabled();

    /**
     * TTL最大随机偏移时间
     */
    @WithName("max-ttl-jitter")
    @WithDefault("PT5M")
    Duration maxTtlJitter();

    /**
     * 布隆过滤器配置
     */
    @WithName("bloom-filter")
    BloomFilterConfig bloomFilter();

    /**
     * 本地缓存配置
     */
    @WithName("local")
    LocalCacheConfig local();

    /**
     * Redis缓存配置
     */
    @WithName("redis")
    RedisCacheConfig redis();

    /**
     * 布隆过滤器配置
     */
    interface BloomFilterConfig {

        /**
         * 是否启用布隆过滤器
         */
        @WithName("enabled")
        @WithDefault("true")
        boolean enabled();

        /**
         * 预期元素数量
         */
        @WithName("expected-insertions")
        @WithDefault("1000000")
        long expectedInsertions();

        /**
         * 误判率
         */
        @WithName("false-positive-rate")
        @WithDefault("0.01")
        double falsePositiveRate();
    }

    /**
     * 本地缓存配置
     */
    interface LocalCacheConfig {

        /**
         * 是否启用本地缓存
         */
        @WithName("enabled")
        @WithDefault("true")
        boolean enabled();

        /**
         * 最大缓存条目数
         */
        @WithName("maximum-size")
        @WithDefault("10000")
        long maximumSize();

        /**
         * 过期时间
         */
        @WithName("expire-after-write")
        @WithDefault("PT10M")
        Duration expireAfterWrite();
    }

    /**
     * Redis缓存配置
     */
    interface RedisCacheConfig {

        /**
         * 是否启用Redis缓存
         */
        @WithName("enabled")
        @WithDefault("true")
        boolean enabled();

        /**
         * Key前缀
         */
        @WithName("key-prefix")
        @WithDefault("admin:")
        String keyPrefix();

        /**
         * 操作超时时间
         */
        @WithName("timeout")
        @WithDefault("PT5S")
        Duration timeout();

        /**
         * 是否启用压缩
         */
        @WithName("compression-enabled")
        @WithDefault("false")
        boolean compressionEnabled();

        /**
         * 压缩阈值（字节）
         */
        @WithName("compression-threshold")
        @WithDefault("1024")
        int compressionThreshold();
    }
}
