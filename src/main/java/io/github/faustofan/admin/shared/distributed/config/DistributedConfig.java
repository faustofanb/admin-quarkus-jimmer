package io.github.faustofan.admin.shared.distributed.config;

import io.github.faustofan.admin.shared.distributed.constants.IdGeneratorType;
import io.github.faustofan.admin.shared.distributed.constants.LockType;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.time.Duration;

/**
 * 分布式配置映射接口
 * <p>
 * 使用 Quarkus ConfigMapping 进行类型安全的配置绑定
 * <p>
 * 配置示例 (application.yaml):
 * <pre>
 * app:
 *   distributed:
 *     enabled: true
 *     lock:
 *       type: REDIS
 *       wait-time: PT10S
 *       lease-time: PT30S
 *     id-generator:
 *       type: SNOWFLAKE
 *       datacenter-id: 1
 *       worker-id: 1
 *     idempotent:
 *       enabled: true
 *       ttl: PT1H
 * </pre>
 */
@ConfigMapping(prefix = "app.distributed")
public interface DistributedConfig {

    /**
     * 是否启用分布式功能
     */
    @WithName("enabled")
    @WithDefault("true")
    boolean enabled();

    /**
     * 锁配置
     */
    @WithName("lock")
    LockConfig lock();

    /**
     * ID生成器配置
     */
    @WithName("id-generator")
    IdGeneratorConfig idGenerator();

    /**
     * 幂等配置
     */
    @WithName("idempotent")
    IdempotentConfig idempotent();

    /**
     * 锁配置
     */
    interface LockConfig {

        /**
         * 锁类型
         */
        @WithName("type")
        @WithDefault("REDIS")
        LockType type();

        /**
         * 默认等待时间
         */
        @WithName("wait-time")
        @WithDefault("PT10S")
        Duration waitTime();

        /**
         * 默认持有时间（租约时间）
         */
        @WithName("lease-time")
        @WithDefault("PT30S")
        Duration leaseTime();

        /**
         * 是否启用看门狗（自动续期）
         */
        @WithName("watchdog-enabled")
        @WithDefault("false")
        boolean watchdogEnabled();

        /**
         * 看门狗检查间隔
         */
        @WithName("watchdog-interval")
        @WithDefault("PT10S")
        Duration watchdogInterval();

        /**
         * 重试间隔
         */
        @WithName("retry-interval")
        @WithDefault("PT0.05S")
        Duration retryInterval();
    }

    /**
     * ID生成器配置
     */
    interface IdGeneratorConfig {

        /**
         * ID生成器类型
         */
        @WithName("type")
        @WithDefault("SNOWFLAKE")
        IdGeneratorType type();

        /**
         * 数据中心ID（雪花算法）
         */
        @WithName("datacenter-id")
        @WithDefault("1")
        long datacenterId();

        /**
         * 机器ID（雪花算法）
         */
        @WithName("worker-id")
        @WithDefault("1")
        long workerId();

        /**
         * 起始时间戳（雪花算法）
         */
        @WithName("epoch")
        @WithDefault("1704067200000")
        long epoch();

        /**
         * Redis Key名称（Redis自增模式）
         */
        @WithName("redis-key")
        @WithDefault("id-generator")
        String redisKey();
    }

    /**
     * 幂等配置
     */
    interface IdempotentConfig {

        /**
         * 是否启用幂等检查
         */
        @WithName("enabled")
        @WithDefault("true")
        boolean enabled();

        /**
         * 幂等记录过期时间
         */
        @WithName("ttl")
        @WithDefault("PT1H")
        Duration ttl();

        /**
         * 是否使用Redis存储
         */
        @WithName("use-redis")
        @WithDefault("true")
        boolean useRedis();

        /**
         * 本地缓存最大条目数
         */
        @WithName("local-max-size")
        @WithDefault("10000")
        long localMaxSize();
    }
}
