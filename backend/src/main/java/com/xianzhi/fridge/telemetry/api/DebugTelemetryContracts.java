package com.xianzhi.fridge.telemetry.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class DebugTelemetryContracts {
    private DebugTelemetryContracts() { }
    public record CreateRequest(@NotNull UUID deviceId, @NotNull UUID sensorId,
                                @NotNull @Pattern(regexp = "NORMAL|TARGET|STALE") String mode,
                                BigDecimal targetValue, @Min(1) @Max(1440) int durationMinutes,
                                @DecimalMin("0") BigDecimal jitter) { }
    public record UpdateRequest(@Pattern(regexp = "NORMAL|TARGET|STALE") String mode,
                                BigDecimal targetValue, @Min(1) @Max(1440) Integer durationMinutes,
                                @DecimalMin("0") BigDecimal jitter, Boolean active) { }
    public record View(UUID id, UUID deviceId, UUID sensorId, String mode, BigDecimal targetValue,
                       int durationMinutes, BigDecimal jitter, String status, Instant startedAt,
                       Instant endsAt, Instant nextEmitAt, Instant lastEmitAt, Instant createdAt, Instant updatedAt) { }
}
