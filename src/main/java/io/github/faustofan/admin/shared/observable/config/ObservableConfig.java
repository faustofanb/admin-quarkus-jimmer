package io.github.faustofan.admin.shared.observable.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.time.Duration;
import java.util.Optional;

/**
 * 可观测性配置
 * <p>
 * 使用 Quarkus ConfigMapping 定义可观测性相关配置
 */
@ConfigMapping(prefix = "app.observable")
public interface ObservableConfig {

    /**
     * 是否启用可观测性
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * 日志配置
     */
    LogConfig log();

    /**
     * 指标配置
     */
    MetricsConfig metrics();

    /**
     * 追踪配置
     */
    TraceConfig trace();

    /**
     * 日志配置
     */
    interface LogConfig {

        /**
         * 是否启用日志增强
         */
        @WithDefault("true")
        boolean enabled();

        /**
         * HTTP日志配置
         */
        HttpLogConfig http();

        /**
         * SQL日志配置
         */
        SqlLogConfig sql();

        /**
         * 业务日志配置
         */
        BusinessLogConfig business();
    }

    /**
     * HTTP日志配置
     */
    interface HttpLogConfig {

        /**
         * 是否启用HTTP日志
         */
        @WithDefault("true")
        boolean enabled();

        /**
         * 是否记录请求头
         */
        @WithDefault("true")
        boolean logHeaders();

        /**
         * 是否记录请求体
         */
        @WithDefault("false")
        boolean logBody();

        /**
         * 请求体最大日志长度
         */
        @WithDefault("2000")
        int maxBodyLength();

        /**
         * 是否使用多行美化格式
         */
        @WithDefault("true")
        boolean prettyPrint();

        /**
         * 需要排除的URI模式（正则）
         */
        Optional<String> excludePatterns();

        /**
         * 敏感头名称（不记录值）
         */
        @WithDefault("Authorization,Cookie,Set-Cookie")
        String sensitiveHeaders();

        /**
         * 慢请求阈值
         */
        @WithDefault("3000")
        long slowThresholdMs();
    }

    /**
     * SQL日志配置
     */
    interface SqlLogConfig {

        /**
         * 是否启用SQL日志
         */
        @WithDefault("true")
        boolean enabled();

        /**
         * 是否记录参数
         */
        @WithDefault("true")
        boolean logParameters();

        /**
         * 是否使用多行美化格式
         */
        @WithDefault("true")
        boolean prettyPrint();

        /**
         * SQL最大日志长度
         */
        @WithDefault("5000")
        int maxLength();

        /**
         * 慢查询阈值（毫秒）
         */
        @WithDefault("1000")
        long slowThresholdMs();

        /**
         * 是否记录返回行数
         */
        @WithDefault("true")
        boolean logRowCount();
    }

    /**
     * 业务日志配置
     */
    interface BusinessLogConfig {

        /**
         * 是否启用业务日志
         */
        @WithDefault("true")
        boolean enabled();

        /**
         * 默认模块名
         */
        @WithDefault("admin")
        String defaultModule();

        /**
         * 是否包含上下文信息
         */
        @WithDefault("true")
        boolean includeContext();
    }

    /**
     * 指标配置
     */
    interface MetricsConfig {

        /**
         * 是否启用指标
         */
        @WithDefault("true")
        boolean enabled();

        /**
         * 采样率（0.0-1.0）
         */
        @WithDefault("1.0")
        double sampleRate();

        /**
         * 是否启用HTTP指标
         */
        @WithDefault("true")
        boolean httpEnabled();

        /**
         * 是否启用SQL指标
         */
        @WithDefault("true")
        boolean sqlEnabled();

        /**
         * 是否启用业务指标
         */
        @WithDefault("true")
        boolean businessEnabled();

        /**
         * 是否启用JVM指标
         */
        @WithDefault("true")
        boolean jvmEnabled();

        /**
         * 百分位数配置
         */
        @WithDefault("0.5,0.75,0.9,0.95,0.99")
        String percentiles();
    }

    /**
     * 追踪配置
     */
    interface TraceConfig {

        /**
         * 是否启用追踪
         */
        @WithDefault("true")
        boolean enabled();

        /**
         * 采样率（0.0-1.0）
         */
        @WithDefault("0.1")
        double sampleRate();

        /**
         * 是否传播上下文
         */
        @WithDefault("true")
        boolean propagateContext();

        /**
         * trace header名称
         */
        @WithDefault("X-Trace-Id")
        String traceIdHeader();

        /**
         * span header名称
         */
        @WithDefault("X-Span-Id")
        String spanIdHeader();
    }
}
