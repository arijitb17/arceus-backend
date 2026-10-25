package com.arijit.monitoringservice.service;

import com.arijit.monitoringservice.config.MonitoringProperties;
import com.arijit.monitoringservice.entity.ServiceMetricSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ServiceMetricsCollector {
    private final RestClient monitoringRestClient;

    public ServiceMetricSnapshot collect(String serviceName, MonitoringProperties.Target target) {
        ServiceMetricSnapshot.ServiceMetricSnapshotBuilder builder = ServiceMetricSnapshot.builder()
                .serviceName(serviceName)
                .capturedAt(Instant.now());

        metric(target, "process.cpu.usage", null)
                .ifPresent(v -> builder.cpuUsagePercent(round(v * 100)));

        metric(target, "jvm.memory.used", null)
                .ifPresent(v -> builder.memoryUsedMb(round(v / (1024.0 * 1024.0))));

        metric(target, "jvm.memory.max", null)
                .ifPresent(v -> builder.memoryMaxMb(round(v / (1024.0 * 1024.0))));

        metric(target, "jvm.memory.used", "area:heap")
                .ifPresent(v -> builder.jvmHeapUsedMb(round(v / (1024.0 * 1024.0))));

        metric(target, "jvm.threads.live", null)
                .ifPresent(v -> builder.threadCount(v.intValue()));

        collectHttpRequestMetrics(target, builder);
        tryCustomMeters(serviceName, target, builder);

        return builder.build();
    }

    private void collectHttpRequestMetrics(MonitoringProperties.Target target, ServiceMetricSnapshot.ServiceMetricSnapshotBuilder builder) {
        Optional<Map<String, Object>> totalBody = fetchRaw(target, "http.server.requests", null);
        totalBody.ifPresent(body -> {
            MetricParsing.statisticValue(body, "COUNT").ifPresent(count -> {
                builder.requestCount(count.longValue());
                MetricParsing.statisticValue(body, "TOTAL_TIME").ifPresent(totalTimeSeconds -> {
                    if (count > 0) {
                        builder.avgResponseTimeMs(round((totalTimeSeconds / count) * 1000.0));
                    }
                });
            });
        });

        Optional<Double> errorCount = metric(target, "http.server.requests", "outcome:SERVER_ERROR", "COUNT");
        errorCount.ifPresent(v -> builder.errorCount(v.longValue()));

        totalBody.flatMap(body -> MetricParsing.statisticValue(body, "COUNT")).ifPresent(total -> {
            double errors = errorCount.orElse(0.0);
            if (total > 0) {
                builder.successRate(round(((total - errors) / total) * 100.0));
            }
        });
    }

    private void tryCustomMeters(String serviceName, MonitoringProperties.Target target, ServiceMetricSnapshot.ServiceMetricSnapshotBuilder builder) {
        metric(target, "cache.gets", "result:hit").ifPresent(hits ->
                metric(target, "cache.gets", "result:miss").ifPresent(misses -> {
                    double total = hits + misses;
                    if (total > 0) {
                        builder.cacheHitRatio(round((hits / total) * 100.0));
                        builder.cacheMissRatio(round((misses / total) * 100.0));
                    }
                }));

        metric(target, "scheduler.jobs.active", null).ifPresent(v -> builder.schedulerActiveJobs(v.intValue()));
        metric(target, "scheduler.jobs.failed", null).ifPresent(v -> builder.schedulerFailedJobs(v.intValue()));
        metric(target, "feature.flag.evaluations", null).ifPresent(v -> builder.featureFlagEvaluations(v.longValue()));
        metric(target, "rate.limit.requests", null).ifPresent(v -> builder.rateLimitRequests(v.longValue()));
        metric(target, "rate.limit.blocked", null).ifPresent(v -> builder.rateLimitBlocked(v.longValue()));
        metric(target, "messaging.queue.size", null).ifPresent(v -> builder.queueSize(v.intValue()));
        metric(target, "http.server.active.connections", null).ifPresent(v -> builder.activeConnections(v.intValue()));
    }

    private Optional<Double> metric(MonitoringProperties.Target target, String name, String tag) {
        return metric(target, name, tag, "VALUE");
    }

    private Optional<Double> metric(MonitoringProperties.Target target, String name, String tag, String statistic) {
        return fetchRaw(target, name, tag).flatMap(body ->
                "VALUE".equals(statistic)
                        ? MetricParsing.firstMeasurementValue(body)
                        : MetricParsing.statisticValue(body, statistic));
    }

    @SuppressWarnings("unchecked")
    private Optional<Map<String, Object>> fetchRaw(MonitoringProperties.Target target, String name, String tag) {
        try {
            String uri = target.baseUrl() + "/actuator/metrics/" + name + (tag != null ? "?tag=" + tag : "");
            Map<String, Object> body = monitoringRestClient.get().uri(uri).retrieve().body(Map.class);
            return Optional.ofNullable(body);
        } catch (Exception e) {
            log.debug("Could not fetch metric '{}' from {}: {}", name, target.baseUrl(), e.toString());
            return Optional.empty();
        }
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
