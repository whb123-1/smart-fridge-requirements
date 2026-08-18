package com.xianzhi.fridge.telemetry.api;

import com.xianzhi.fridge.fridge.domain.SensorMetric;
import com.xianzhi.fridge.telemetry.domain.EnvironmentStatus;
import com.xianzhi.fridge.telemetry.domain.ReadingQuality;
import com.xianzhi.fridge.telemetry.domain.ReadingSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class EnvironmentContracts {
    private EnvironmentContracts() { }
    public record ReadingView(UUID id, UUID sensorId, SensorMetric metric, BigDecimal value, String unit,
                              ReadingQuality quality, ReadingSource source, Instant observedAt, Instant receivedAt) { }
    public record SafeRange(BigDecimal min, BigDecimal max, String unit) { }
    public record MetricView(SensorMetric metric, EnvironmentStatus status, BigDecimal valueCelsius,
                             BigDecimal displayValue, String displayUnit, String quality,
                             SafeRange safeRange, Instant lastObservedAt, Instant lastReceivedAt,
                             Instant stateSince) { }
    public record IncidentView(UUID id, SensorMetric metric, String reason, String direction, String severity,
                               Instant startedAt, Instant lastObservedAt, BigDecimal maxDeviation) { }
    public record ZoneView(UUID id, String name, String kind, EnvironmentStatus status,
                           int boundSensorCount, int onlineSensorCount, int staleSensorCount,
                           List<MetricView> metrics, List<IncidentView> activeIncidents,
                           List<UUID> affectedBatchIds) { }
    public record FridgeView(UUID fridgeId, String fridgeName, String temperatureUnit,
                             int boundSensorCount, int onlineSensorCount, int staleSensorCount,
                             Instant lastSyncedAt, List<ZoneView> zones) { }
}
