package com.xianzhi.fridge.telemetry.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.xianzhi.fridge.fridge.domain.SensorMetric;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TelemetrySimulatorTest {
    @Test
    void regularReadingsStayInsideUserIdealRange() {
        BigDecimal value = TelemetrySimulator.simulatedValue(SensorMetric.TEMPERATURE,
                new BigDecimal("4.2"), new BigDecimal("4"), new BigDecimal("1"), new BigDecimal("7"),
                new BigDecimal("-40"), new BigDecimal("60"), 0.80, 0.95);
        assertThat(value).isBetween(new BigDecimal("1"), new BigDecimal("7"));
        assertThat(value).isCloseTo(new BigDecimal("4"), org.assertj.core.data.Offset.offset(new BigDecimal("1")));
    }

    @Test
    void rareIncidentRollMayExceedIdealRangeButNeverPhysicalRange() {
        BigDecimal value = TelemetrySimulator.simulatedValue(SensorMetric.HUMIDITY,
                null, new BigDecimal("70"), new BigDecimal("60"), new BigDecimal("80"),
                BigDecimal.ZERO, new BigDecimal("100"), 0.01, 0.90);
        assertThat(value).isGreaterThan(new BigDecimal("80"));
        assertThat(value).isLessThanOrEqualTo(new BigDecimal("100"));
    }
}
