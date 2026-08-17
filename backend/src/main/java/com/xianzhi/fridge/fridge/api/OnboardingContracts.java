package com.xianzhi.fridge.fridge.api;

import com.xianzhi.fridge.fridge.domain.ZoneKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class OnboardingContracts {
    private OnboardingContracts() { }

    public record InitializeRequest(
            @NotBlank @Size(max = 80) String fridgeName,
            @NotNull @Size(min = 3, max = 6) List<@Valid ZoneRequest> zones) { }

    public record ZoneRequest(
            @NotNull ZoneKind kind,
            @NotBlank @Size(max = 48) String name,
            @Min(0) @Max(4) int temperatureSensorCount,
            @Min(0) @Max(4) int humiditySensorCount) { }

    public record ZoneDefault(
            ZoneKind kind, String suggestedName,
            BigDecimal targetTemperatureC, BigDecimal targetHumidityPct,
            BigDecimal safeTemperatureMinC, BigDecimal safeTemperatureMaxC,
            BigDecimal safeHumidityMinPct, BigDecimal safeHumidityMaxPct) { }

    public record ZoneSummary(
            UUID id, ZoneKind kind, String name, boolean enabled,
            BigDecimal targetTemperatureC, BigDecimal targetHumidityPct,
            BigDecimal safeTemperatureMinC, BigDecimal safeTemperatureMaxC,
            BigDecimal safeHumidityMinPct, BigDecimal safeHumidityMaxPct,
            int temperatureSensorCount, int humiditySensorCount, String sensorBindingStatus) { }

    public record FridgeSummary(UUID id, String name, List<ZoneSummary> zones) { }

    public record Status(
            boolean completed, int minimumZones, int maximumZones, int maximumSensorsPerMetric,
            List<ZoneDefault> zoneDefaults, FridgeSummary fridge) { }
}
