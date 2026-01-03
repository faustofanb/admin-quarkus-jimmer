package io.github.faustofan.admin.shared.cache.constants;

import java.time.Duration;

/**
 * 缓存基础设施常量定义
 * <p>
 * 集中管理所有缓存相关的常量，避免魔法字符串
 */
public final class CacheConstants {

    private CacheConstants() {
        // 禁止实例化
    }

    // ===========================
    // 默认配置
    // ===========================

    /**
     * 默认缓存过期时间（秒）
     */
    public static final long DEFAULT_TTL_SECONDS = 3600L;

    /**
     * 默认缓存过期时间
     */
    public static final Duration DEFAULT_TTL = Duration.ofSeconds(DEFAULT_TTL_SECONDS);

    /**
     * 短期缓存过期时间（5分钟）
     */
    public static final Duration SHORT_TTL = Duration.ofMinutes(5);

    /**
     * 中期缓存过期时间（30分钟）
     */
    public static final Duration MEDIUM_TTL = Duration.ofMinutes(30);

    /**
     * 长期缓存过期时间（24小时）
     */
    public static final Duration LONG_TTL = Duration.ofHours(24);

    /**
     * 空值缓存过期时间（用于防止缓存穿透）
     */
    public static final Duration NULL_VALUE_TTL = Duration.ofMinutes(2);

    /**
     * 最大TTL随机偏移量（秒）- 用于防止缓存雪崩
     */
    public static final long MAX_TTL_JITTER_SECONDS = 300L;

    // ===========================
    // 布隆过滤器配置
    // ===========================

    /**
     * 默认布隆过滤器预期元素数量
     */
    public static final long DEFAULT_BLOOM_EXPECTED_INSERTIONS = 1_000_000L;

    /**
     * 默认布隆过滤器误判率
     */
    public static final double DEFAULT_BLOOM_FALSE_POSITIVE_RATE = 0.01;

    // ===========================
    // 缓存Key前缀
    // ===========================

    /**
     * 缓存Key命名空间
     */
    public static final class KeyPrefix {
        private KeyPrefix() {}

        /**
         * 全局Key前缀
         */
        public static final String GLOBAL = "admin:";

        /**
         * 用户相关缓存
         */
        public static final String USER = GLOBAL + "user:";

        /**
         * 角色相关缓存
         */
        public static final String ROLE = GLOBAL + "role:";

        /**
         * 菜单相关缓存
         */
        public static final String MENU = GLOBAL + "menu:";

        /**
         * 权限相关缓存
         */
        public static final String PERMISSION = GLOBAL + "perm:";

        /**
         * 字典相关缓存
         */
        public static final String DICT = GLOBAL + "dict:";

        /**
         * 租户相关缓存
         */
        public static final String TENANT = GLOBAL + "tenant:";

        /**
         * 配置相关缓存
         */
        public static final String CONFIG = GLOBAL + "config:";

        /**
         * 验证码缓存
         */
        public static final String CAPTCHA = GLOBAL + "captcha:";

        /**
         * Token相关缓存
         */
        public static final String TOKEN = GLOBAL + "token:";

        /**
         * 空值占位符Key后缀
         */
        public static final String NULL_PLACEHOLDER = GLOBAL + "null:";

        /**
         * 布隆过滤器Key前缀
         */
        public static final String BLOOM_FILTER = GLOBAL + "bloom:";

        /**
         * 分布式锁Key前缀
         */
        public static final String LOCK = GLOBAL + "lock:";
    }

    // ===========================
    // 本地缓存名称
    // ===========================

    /**
     * Quarkus Cache 缓存名称定义
     */
    public static final class LocalCacheName {
        private LocalCacheName() {}

        /**
         * 用户信息缓存
         */
        public static final String USER_CACHE = "user-cache";

        /**
         * 角色信息缓存
         */
        public static final String ROLE_CACHE = "role-cache";

        /**
         * 菜单信息缓存
         */
        public static final String MENU_CACHE = "menu-cache";

        /**
         * 权限信息缓存
         */
        public static final String PERMISSION_CACHE = "permission-cache";

        /**
         * 字典信息缓存
         */
        public static final String DICT_CACHE = "dict-cache";

        /**
         * 租户信息缓存
         */
        public static final String TENANT_CACHE = "tenant-cache";

        /**
         * 配置信息缓存
         */
        public static final String CONFIG_CACHE = "config-cache";

        /**
         * 通用缓存
         */
        public static final String GENERIC_CACHE = "generic-cache";
    }

    // ===========================
    // 布隆过滤器名称
    // ===========================

    /**
     * 布隆过滤器名称定义
     */
    public static final class BloomFilterName {
        private BloomFilterName() {}

        /**
         * 用户ID布隆过滤器
         */
        public static final String USER_ID = "user-id-bloom";

        /**
         * 用户名布隆过滤器
         */
        public static final String USERNAME = "username-bloom";

        /**
         * 角色ID布隆过滤器
         */
        public static final String ROLE_ID = "role-id-bloom";

        /**
         * 菜单ID布隆过滤器
         */
        public static final String MENU_ID = "menu-id-bloom";

        /**
         * 租户ID布隆过滤器
         */
        public static final String TENANT_ID = "tenant-id-bloom";
    }

    // ===========================
    // 特殊值标记
    // ===========================

    /**
     * 空值占位符（用于防止缓存穿透）
     */
    public static final String NULL_PLACEHOLDER_VALUE = "__NULL__";

    /**
     * 缓存数据分隔符
     */
    public static final String KEY_SEPARATOR = ":";
}
