package io.github.faustofan.admin.shared.distributed.constants;

import java.time.Duration;

/**
 * 分布式基础设施常量定义
 * <p>
 * 集中管理所有分布式相关的常量，避免魔法字符串
 */
public final class DistributedConstants {

    private DistributedConstants() {
        // 禁止实例化
    }

    // ===========================
    // 分布式锁配置
    // ===========================

    /**
     * 默认锁等待时间（秒）
     */
    public static final long DEFAULT_LOCK_WAIT_SECONDS = 10L;

    /**
     * 默认锁持有时间（秒）
     */
    public static final long DEFAULT_LOCK_LEASE_SECONDS = 30L;

    /**
     * 默认锁等待时间
     */
    public static final Duration DEFAULT_LOCK_WAIT_TIME = Duration.ofSeconds(DEFAULT_LOCK_WAIT_SECONDS);

    /**
     * 默认锁持有时间
     */
    public static final Duration DEFAULT_LOCK_LEASE_TIME = Duration.ofSeconds(DEFAULT_LOCK_LEASE_SECONDS);

    /**
     * 锁重试间隔（毫秒）
     */
    public static final long LOCK_RETRY_INTERVAL_MS = 50L;

    // ===========================
    // 雪花算法配置
    // ===========================

    /**
     * 默认数据中心ID
     */
    public static final long DEFAULT_DATACENTER_ID = 1L;

    /**
     * 默认机器ID
     */
    public static final long DEFAULT_WORKER_ID = 1L;

    /**
     * 雪花算法起始时间戳（2024-01-01 00:00:00）
     */
    public static final long SNOWFLAKE_EPOCH = 1704067200000L;

    /**
     * 数据中心ID占用位数
     */
    public static final int DATACENTER_ID_BITS = 5;

    /**
     * 机器ID占用位数
     */
    public static final int WORKER_ID_BITS = 5;

    /**
     * 序列号占用位数
     */
    public static final int SEQUENCE_BITS = 12;

    /**
     * 最大数据中心ID
     */
    public static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);

    /**
     * 最大机器ID
     */
    public static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);

    /**
     * 最大序列号
     */
    public static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);

    // ===========================
    // 幂等配置
    // ===========================

    /**
     * 默认幂等检查过期时间（秒）
     */
    public static final long DEFAULT_IDEMPOTENT_TTL_SECONDS = 3600L;

    /**
     * 默认幂等检查过期时间
     */
    public static final Duration DEFAULT_IDEMPOTENT_TTL = Duration.ofSeconds(DEFAULT_IDEMPOTENT_TTL_SECONDS);

    // ===========================
    // Redis Key前缀
    // ===========================

    /**
     * 分布式Key命名空间
     */
    public static final class KeyPrefix {
        private KeyPrefix() {}

        /**
         * 全局Key前缀
         */
        public static final String GLOBAL = "admin:";

        /**
         * 分布式锁Key前缀
         */
        public static final String LOCK = GLOBAL + "lock:";

        /**
         * 幂等Key前缀
         */
        public static final String IDEMPOTENT = GLOBAL + "idempotent:";

        /**
         * ID生成器Key前缀
         */
        public static final String ID_GENERATOR = GLOBAL + "id:";

        /**
         * 缓存击穿保护锁前缀
         */
        public static final String CACHE_BREAKDOWN_LOCK = LOCK + "cache:";
    }

    // ===========================
    // 锁名称定义
    // ===========================

    /**
     * 预定义锁名称
     */
    public static final class LockName {
        private LockName() {}

        /**
         * 用户创建锁
         */
        public static final String USER_CREATE = "user-create";

        /**
         * 角色创建锁
         */
        public static final String ROLE_CREATE = "role-create";

        /**
         * 菜单创建锁
         */
        public static final String MENU_CREATE = "menu-create";

        /**
         * 租户创建锁
         */
        public static final String TENANT_CREATE = "tenant-create";

        /**
         * 字典创建锁
         */
        public static final String DICT_CREATE = "dict-create";

        /**
         * 缓存加载锁
         */
        public static final String CACHE_LOAD = "cache-load";
    }

    // ===========================
    // 特殊值标记
    // ===========================

    /**
     * 锁持有者标识分隔符
     */
    public static final String LOCK_HOLDER_SEPARATOR = ":";

    /**
     * Key分隔符
     */
    public static final String KEY_SEPARATOR = ":";
}
