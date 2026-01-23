package io.github.faustofan.admin.shared.observable.metrics;

import io.github.faustofan.admin.shared.observable.config.ObservableConfig;
import io.github.faustofan.admin.shared.observable.constants.ObservableConstants;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MetricsRecorder 负责统一的指标收集与上报。
 * <p>
 * 使用 Micrometer 的 {@link MeterRegistry}，在 Quarkus 环境下会自动绑定到 Prometheus、OpenTelemetry 等后端。
 * 所有指标的开启/关闭、采样率等由 {@link ObservableConfig} 控制，避免在代码中出现硬编码的魔法字符串。
 */
@ApplicationScoped
public class MetricsRecorder {

    private final MeterRegistry meterRegistry;
    private final ObservableConfig config;

    @Inject
    public MetricsRecorder(MeterRegistry meterRegistry, ObservableConfig config) {
        this.meterRegistry = meterRegistry;
        this.config = config;
    }

    /**
     * 增加计数器（counter）
     *
     * @param metricName 完整的指标名称，例如 {@link ObservableConstants.HttpMetric#REQUESTS_TOTAL}
     * @param tags       额外的标签键值对，可为空
     */
    public void incrementCounter(String metricName, Map<String, String> tags) {
        if (!config.metrics().enabled()) {
            return;
        }
        meterRegistry.counter(metricName, toTags(tags)).increment();
    }

    /**
     * 记录计时器（timer）
     *
     * @param metricName 完整的指标名称，例如 {@link ObservableConstants.HttpMetric#REQUEST_DURATION}
     * @param durationMs 持续时间，单位毫秒
     * @param tags       额外的标签键值对，可为空
     */
    public void recordTimer(String metricName, long durationMs, Map<String, String> tags) {
        if (!config.metrics().enabled()) {
            return;
        }
        meterRegistry.timer(metricName, toTags(tags)).record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * 记录直方图（histogram）或分布摘要（distribution summary），取决于底层实现。
     *
     * @param metricName 完整的指标名称，例如 {@link ObservableConstants.SqlMetric#QUERY_DURATION}
     * @param value      需要记录的数值
     * @param tags       额外的标签键值对，可为空
     */
    public void recordHistogram(String metricName, double value, Map<String, String> tags) {
        if (!config.metrics().enabled()) {
            return;
        }
        meterRegistry.summary(metricName, toTags(tags)).record(value);
    }

    /**
     * 将 Map 转换为 Micrometer 所需的 {@link Tag} 列表。
     */
    private Iterable<Tag> toTags(Map<String, String> tags) {
        if (tags == null || tags.isEmpty()) {
            return Tags.empty();
        }
        return tags.entrySet().stream()
                .map(e -> Tag.of(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }
}
