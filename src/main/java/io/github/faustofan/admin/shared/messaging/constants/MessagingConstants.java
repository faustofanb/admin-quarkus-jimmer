package io.github.faustofan.admin.shared.messaging.constants;

import java.time.Duration;

/**
 * 消息基础设施常量定义
 * <p>
 * 集中管理所有消息相关的常量，避免魔法字符串
 */
public final class MessagingConstants {

    private MessagingConstants() {
        // 禁止实例化
    }

    // ===========================
    // 默认配置
    // ===========================

    /**
     * 默认消息发送超时时间（秒）
     */
    public static final long DEFAULT_SEND_TIMEOUT_SECONDS = 30L;

    /**
     * 默认消息发送超时时间
     */
    public static final Duration DEFAULT_SEND_TIMEOUT = Duration.ofSeconds(DEFAULT_SEND_TIMEOUT_SECONDS);

    /**
     * 默认消息重试次数
     */
    public static final int DEFAULT_RETRY_COUNT = 3;

    /**
     * 默认重试间隔（毫秒）
     */
    public static final long DEFAULT_RETRY_INTERVAL_MS = 1000L;

    /**
     * 默认重试间隔
     */
    public static final Duration DEFAULT_RETRY_INTERVAL = Duration.ofMillis(DEFAULT_RETRY_INTERVAL_MS);

    /**
     * 默认批量大小
     */
    public static final int DEFAULT_BATCH_SIZE = 100;

    /**
     * 默认流缓冲区大小
     */
    public static final int DEFAULT_STREAM_BUFFER_SIZE = 256;

    // ===========================
    // Pulsar 配置
    // ===========================

    /**
     * 默认 Pulsar 租户
     */
    public static final String DEFAULT_PULSAR_TENANT = "public";

    /**
     * 默认 Pulsar 命名空间
     */
    public static final String DEFAULT_PULSAR_NAMESPACE = "default";

    /**
     * 默认消费者订阅名称前缀
     */
    public static final String DEFAULT_SUBSCRIPTION_PREFIX = "admin-sub-";

    // ===========================
    // Topic 前缀与命名
    // ===========================

    /**
     * Topic 命名空间
     */
    public static final class TopicPrefix {
        private TopicPrefix() {}

        /**
         * 全局Topic前缀
         */
        public static final String GLOBAL = "admin.";

        /**
         * 系统事件Topic前缀
         */
        public static final String SYSTEM = GLOBAL + "system.";

        /**
         * 业务事件Topic前缀
         */
        public static final String BUSINESS = GLOBAL + "business.";

        /**
         * 领域事件Topic前缀
         */
        public static final String DOMAIN = GLOBAL + "domain.";

        /**
         * 集成事件Topic前缀
         */
        public static final String INTEGRATION = GLOBAL + "integration.";

        /**
         * 审计事件Topic前缀
         */
        public static final String AUDIT = GLOBAL + "audit.";
    }

    // ===========================
    // 预定义 Topic 名称
    // ===========================

    /**
     * 系统预定义 Topic
     */
    public static final class SystemTopic {
        private SystemTopic() {}

        /**
         * 用户事件
         */
        public static final String USER_EVENTS = TopicPrefix.SYSTEM + "user";

        /**
         * 角色事件
         */
        public static final String ROLE_EVENTS = TopicPrefix.SYSTEM + "role";

        /**
         * 菜单事件
         */
        public static final String MENU_EVENTS = TopicPrefix.SYSTEM + "menu";

        /**
         * 租户事件
         */
        public static final String TENANT_EVENTS = TopicPrefix.SYSTEM + "tenant";

        /**
         * 配置变更事件
         */
        public static final String CONFIG_EVENTS = TopicPrefix.SYSTEM + "config";

        /**
         * 缓存失效事件
         */
        public static final String CACHE_INVALIDATION = TopicPrefix.SYSTEM + "cache-invalidation";
    }

    /**
     * 业务预定义 Topic
     */
    public static final class BusinessTopic {
        private BusinessTopic() {}

        /**
         * 订单事件
         */
        public static final String ORDER_EVENTS = TopicPrefix.BUSINESS + "order";

        /**
         * 支付事件
         */
        public static final String PAYMENT_EVENTS = TopicPrefix.BUSINESS + "payment";

        /**
         * 通知事件
         */
        public static final String NOTIFICATION_EVENTS = TopicPrefix.BUSINESS + "notification";
    }

    /**
     * 审计 Topic
     */
    public static final class AuditTopic {
        private AuditTopic() {}

        /**
         * 操作日志
         */
        public static final String OPERATION_LOG = TopicPrefix.AUDIT + "operation";

        /**
         * 登录日志
         */
        public static final String LOGIN_LOG = TopicPrefix.AUDIT + "login";

        /**
         * 访问日志
         */
        public static final String ACCESS_LOG = TopicPrefix.AUDIT + "access";
    }

    /**
     * 实体变更事件 Topic
     * <p>
     * 由 Jimmer 触发器自动发布的实体生命周期事件
     * Topic 格式: admin.domain.{entitySimpleName.toLowerCase()}
     */
    public static final class EntityTopic {
        private EntityTopic() {}

        /**
         * 用户实体变更事件
         */
        public static final String SYSTEM_USER = TopicPrefix.DOMAIN + "systemuser";

        /**
         * 角色实体变更事件
         */
        public static final String SYSTEM_ROLE = TopicPrefix.DOMAIN + "systemrole";

        /**
         * 菜单实体变更事件
         */
        public static final String SYSTEM_MENU = TopicPrefix.DOMAIN + "systemmenu";

        /**
         * 部门实体变更事件
         */
        public static final String SYSTEM_DEPT = TopicPrefix.DOMAIN + "systemdept";

        /**
         * 岗位实体变更事件
         */
        public static final String SYSTEM_POST = TopicPrefix.DOMAIN + "systempost";

        /**
         * 租户实体变更事件
         */
        public static final String SYSTEM_TENANT = TopicPrefix.DOMAIN + "systemtenant";

        /**
         * 租户套餐实体变更事件
         */
        public static final String SYSTEM_TENANT_PACKAGE = TopicPrefix.DOMAIN + "systemtenantpackage";

        /**
         * 字典类型实体变更事件
         */
        public static final String SYSTEM_DICT_TYPE = TopicPrefix.DOMAIN + "systemdicttype";

        /**
         * 字典数据实体变更事件
         */
        public static final String SYSTEM_DICT_DATA = TopicPrefix.DOMAIN + "systemdictdata";

        /**
         * 根据实体类名生成 Topic
         *
         * @param entityClass 实体类
         * @return Topic 名称
         */
        public static String forEntity(Class<?> entityClass) {
            return TopicPrefix.DOMAIN + entityClass.getSimpleName().toLowerCase();
        }
    }

    // ===========================
    // 消费者组名称
    // ===========================

    /**
     * 消费者组定义
     */
    public static final class ConsumerGroup {
        private ConsumerGroup() {}

        /**
         * 默认消费者组
         */
        public static final String DEFAULT = "admin-group";

        /**
         * 审计消费者组
         */
        public static final String AUDIT = "admin-audit-group";

        /**
         * 通知消费者组
         */
        public static final String NOTIFICATION = "admin-notification-group";

        /**
         * 缓存同步消费者组
         */
        public static final String CACHE_SYNC = "admin-cache-sync-group";
    }

    // ===========================
    // 消息头常量
    // ===========================

    /**
     * 消息头定义
     */
    public static final class Header {
        private Header() {}

        /**
         * 消息ID
         */
        public static final String MESSAGE_ID = "x-message-id";

        /**
         * 消息类型
         */
        public static final String MESSAGE_TYPE = "x-message-type";

        /**
         * 事件类型
         */
        public static final String EVENT_TYPE = "x-event-type";

        /**
         * 追踪ID
         */
        public static final String TRACE_ID = "x-trace-id";

        /**
         * 租户ID
         */
        public static final String TENANT_ID = "x-tenant-id";

        /**
         * 用户ID
         */
        public static final String USER_ID = "x-user-id";

        /**
         * 时间戳
         */
        public static final String TIMESTAMP = "x-timestamp";

        /**
         * 来源服务
         */
        public static final String SOURCE = "x-source";

        /**
         * 重试次数
         */
        public static final String RETRY_COUNT = "x-retry-count";

        /**
         * 延迟等级（用于延迟消息）
         */
        public static final String DELAY_LEVEL = "x-delay-level";
    }

    // ===========================
    // 特殊值
    // ===========================

    /**
     * Topic分隔符
     */
    public static final String TOPIC_SEPARATOR = ".";

    /**
     * 应用名称（用于消息来源标识）
     */
    public static final String APPLICATION_NAME = "admin-server";
}
