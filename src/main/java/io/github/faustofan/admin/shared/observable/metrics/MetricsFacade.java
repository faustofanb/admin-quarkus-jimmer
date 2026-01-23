package io.github.faustofan.admin.shared.observable.metrics;

import io.github.faustofan.admin.shared.observable.constants.ObservableConstants;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;

/**
 * Facade for metrics, exposing a simple API for business code.
 * Internally delegates to {@link MetricsRecorder} which uses Micrometer.
 */
@ApplicationScoped
public class MetricsFacade {

    @Inject
    MetricsRecorder recorder;

    /** Increment a counter metric */
    public void incrementCounter(String metricName, Map<String, String> tags) {
        recorder.incrementCounter(metricName, tags);
    }

    /** Record a timer (duration in ms) */
    public void recordTimer(String metricName, long durationMs, Map<String, String> tags) {
        recorder.recordTimer(metricName, durationMs, tags);
    }

    /** Record a histogram / distribution summary */
    public void recordHistogram(String metricName, double value, Map<String, String> tags) {
        recorder.recordHistogram(metricName, value, tags);
    }
}
