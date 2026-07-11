package com.arijit.monitoringservice.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MetricParsingTest {

    @Test
    void firstMeasurementValueReadsSingleValueMeasurement() {
        Map<String, Object> body = Map.of(
                "name", "process.cpu.usage",
                "measurements", List.of(Map.of("statistic", "VALUE", "value", 0.42))
        );

        Optional<Double> value = MetricParsing.firstMeasurementValue(body);

        assertThat(value).contains(0.42);
    }

    @Test
    void firstMeasurementValueReturnsEmptyForMissingMeasurements() {
        assertThat(MetricParsing.firstMeasurementValue(null)).isEmpty();
        assertThat(MetricParsing.firstMeasurementValue(Map.of("name", "x"))).isEmpty();
    }

    @Test
    void statisticValueFindsMatchingStatisticAmongMultipleMeasurements() {
        Map<String, Object> body = Map.of(
                "measurements", List.of(
                        Map.of("statistic", "COUNT", "value", 120.0),
                        Map.of("statistic", "TOTAL_TIME", "value", 3.6)
                )
        );

        assertThat(MetricParsing.statisticValue(body, "COUNT")).contains(120.0);
        assertThat(MetricParsing.statisticValue(body, "TOTAL_TIME")).contains(3.6);
        assertThat(MetricParsing.statisticValue(body, "MAX")).isEmpty();
    }
}
