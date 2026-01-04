package io.github.faustofan.admin.shared.messaging.config;

import io.github.faustofan.admin.shared.messaging.constants.ChannelType;
import io.github.faustofan.admin.shared.messaging.constants.DeliveryMode;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.time.Duration;

/**
 * 消息基础设施配置
 * <p>
 * 使用 SmallRye Config Mapping，配置前缀为 admin.messaging
 */
@ConfigMapping(prefix = "app.messaging")
public interface MessagingConfig {

    /**
     * 是否启用消息基础设施
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * 默认消息通道类型
     */
    @WithName("channel")
    @WithDefault("local")
    ChannelType channel();

    /**
     * 默认投递模式
     */
    @WithName("delivery-mode")
    @WithDefault("at_least_once")
    DeliveryMode deliveryMode();

    /**
     * 本地事件配置
     */
    LocalEventConfig local();

    /**
     * Pulsar 配置
     */
    PulsarConfig pulsar();

    /**
     * 流配置
     */
    StreamConfig stream();

    /**
     * 重试配置
     */
    RetryConfig retry();

    /**
     * 本地事件配置
     */
    interface LocalEventConfig {

        /**
         * 是否启用本地事件
         */
        @WithDefault("true")
        boolean enabled();

        /**
         * 是否异步处理本地事件
         */
        @WithName("async")
        @WithDefault("true")
        boolean async();

        /**
         * 异步处理超时时间
         */
        @WithName("async-timeout")
        @WithDefault("30s")
        Duration asyncTimeout();
    }

    /**
     * Pulsar 配置
     */
    interface PulsarConfig {

        /**
         * 是否启用 Pulsar（可插拔开关）
         */
        @WithDefault("false")
        boolean enabled();

        /**
         * Pulsar 服务地址
         */
        @WithName("service-url")
        @WithDefault("pulsar://localhost:6650")
        String serviceUrl();

        /**
         * 默认租户
         */
        @WithDefault("public")
        String tenant();

        /**
         * 默认命名空间
         */
        @WithDefault("default")
        String namespace();

        /**
         * 订阅名称前缀
         */
        @WithName("subscription-prefix")
        @WithDefault("admin-sub-")
        String subscriptionPrefix();

        /**
         * Producer 发送超时
         */
        @WithName("send-timeout")
        @WithDefault("30s")
        Duration sendTimeout();

        /**
         * 批量发送配置
         */
        BatchingConfig batching();

        /**
         * Producer 批量发送配置
         */
        interface BatchingConfig {

            /**
             * 是否启用批量发送
             */
            @WithDefault("true")
            boolean enabled();

            /**
             * 批量最大消息数
             */
            @WithName("max-messages")
            @WithDefault("100")
            int maxMessages();

            /**
             * 批量最大延迟
             */
            @WithName("max-delay")
            @WithDefault("10ms")
            Duration maxDelay();
        }
    }

    /**
     * 流配置
     */
    interface StreamConfig {

        /**
         * 是否启用流模式
         */
        @WithDefault("true")
        boolean enabled();

        /**
         * 缓冲区大小
         */
        @WithName("buffer-size")
        @WithDefault("256")
        int bufferSize();

        /**
         * 背压策略
         */
        @WithName("backpressure-strategy")
        @WithDefault("buffer")
        String backpressureStrategy();
    }

    /**
     * 重试配置
     */
    interface RetryConfig {

        /**
         * 最大重试次数
         */
        @WithName("max-attempts")
        @WithDefault("3")
        int maxAttempts();

        /**
         * 重试间隔
         */
        @WithDefault("1s")
        Duration interval();

        /**
         * 是否使用指数退避
         */
        @WithName("exponential-backoff")
        @WithDefault("true")
        boolean exponentialBackoff();

        /**
         * 最大重试间隔（使用指数退避时）
         */
        @WithName("max-interval")
        @WithDefault("30s")
        Duration maxInterval();
    }
}
