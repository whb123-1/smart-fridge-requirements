package com.xianzhi.fridge.telemetry.api;

import com.xianzhi.fridge.fridge.domain.SensorMetric;
import com.xianzhi.fridge.telemetry.domain.ReadingQuality;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TelemetryContracts {
    private TelemetryContracts() { }
    public record Message(UUID messageId, Instant observedAt, String firmwareVersion, List<Reading> readings) { }
    public record Reading(UUID sensorId, SensorMetric metric, BigDecimal value, String unit, ReadingQuality quality) { }
}
