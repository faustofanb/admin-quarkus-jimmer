package io.github.faustofan.admin.shared.async.constants;

/**
 * 异步基础设施常量定义
 * <p>
 * 集中管理所有异步相关的常量，避免魔法字符串
 */
public final class AsyncConstants {

    private AsyncConstants() {
        // 禁止实例化
    }

    // ===========================
    // 虚拟线程配置
    // ===========================

    /**
     * 虚拟线程名称前缀
     */
    public static final String VIRTUAL_THREAD_NAME_PREFIX = "vt-async-";

    /**
     * 默认异步任务超时时间（毫秒）
     */
    public static final long DEFAULT_TIMEOUT_MS = 30_000L;

    /**
     * 批量任务默认并发度
     */
    public static final int DEFAULT_BATCH_CONCURRENCY = 10;

    // ===========================
    // MDC透传Key
    // ===========================

    /**
     * MDC 上下文 Key 常量
     * <p>
     * 用于日志诊断信息的 Key 定义
     */
    public static final class MdcKeys {
        private MdcKeys() {}

        /**
         * 请求追踪ID
         */
        public static final String TRACE_ID = "traceId";

        /**
         * 请求跨度ID
         */
        public static final String SPAN_ID = "spanId";

        /**
         * 用户ID
         */
        public static final String USER_ID = "userId";

        /**
         * 租户ID
         */
        public static final String TENANT_ID = "tenantId";

        /**
         * 请求URI
         */
        public static final String REQUEST_URI = "requestUri";

        /**
         * 请求方法
         */
        public static final String REQUEST_METHOD = "requestMethod";

        /**
         * 客户端IP
         */
        public static final String CLIENT_IP = "clientIp";
    }
}
