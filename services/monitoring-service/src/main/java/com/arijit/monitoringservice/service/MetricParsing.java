package com.arijit.monitoringservice.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

final class MetricParsing {
    private MetricParsing() {
    }

    static Optional<Double> firstMeasurementValue(Map<String, Object> body) {
        if (body == null) {
            return Optional.empty();
        }
        Object measurements = body.get("measurements");
        if (!(measurements instanceof List<?> list) || list.isEmpty()) {
            return Optional.empty();
        }
        Object first = list.get(0);
        if (!(first instanceof Map<?, ?> map)) {
            return Optional.empty();
        }
        Object value = map.get("value");
        if (value instanceof Number number) {
            return Optional.of(number.doubleValue());
        }
        return Optional.empty();
    }

    static Optional<Double> statisticValue(Map<String, Object> body, String statistic) {
        if (body == null) {
            return Optional.empty();
        }
        Object measurements = body.get("measurements");
        if (!(measurements instanceof List<?> list)) {
            return Optional.empty();
        }
        for (Object entry : list) {
            if (entry instanceof Map<?, ?> map
                    && statistic.equalsIgnoreCase(String.valueOf(map.get("statistic")))
                    && map.get("value") instanceof Number number) {
                return Optional.of(number.doubleValue());
            }
        }
        return Optional.empty();
    }
}
