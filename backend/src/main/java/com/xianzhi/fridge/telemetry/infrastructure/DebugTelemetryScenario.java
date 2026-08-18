package com.xianzhi.fridge.telemetry.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "debug_telemetry_scenario")
public class DebugTelemetryScenario {
    @Id @JdbcTypeCode(Types.BINARY) private UUID id;
    @JdbcTypeCode(Types.BINARY) @Column(name = "user_id", nullable = false) private UUID userId;
    @JdbcTypeCode(Types.BINARY) @Column(name = "device_id", nullable = false) private UUID deviceId;
    @JdbcTypeCode(Types.BINARY) @Column(name = "sensor_id", nullable = false) private UUID sensorId;
    @Column(nullable = false, length = 16) private String mode;
    @Column(name = "target_value", precision = 10, scale = 3) private BigDecimal targetValue;
    @Column(name = "duration_minutes", nullable = false) private int durationMinutes;
    @Column(nullable = false, precision = 10, scale = 3) private BigDecimal jitter;
    @Column(nullable = false, length = 16) private String status;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "ends_at", nullable = false) private Instant endsAt;
    @Column(name = "next_emit_at", nullable = false) private Instant nextEmitAt;
    @Column(name = "last_emit_at") private Instant lastEmitAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;
    protected DebugTelemetryScenario() { }
    public DebugTelemetryScenario(UUID id, UUID userId, UUID deviceId, UUID sensorId, String mode,
                                  BigDecimal targetValue, int durationMinutes, BigDecimal jitter, Instant now) {
        this.id = id; this.userId = userId; this.deviceId = deviceId; this.sensorId = sensorId; this.mode = mode;
        this.targetValue = targetValue; this.durationMinutes = durationMinutes; this.jitter = jitter;
        this.status = "ACTIVE"; this.startedAt = now; this.endsAt = now.plus(Duration.ofMinutes(durationMinutes));
        this.nextEmitAt = now; this.createdAt = this.updatedAt = now;
    }
    public void update(String mode, BigDecimal targetValue, Integer durationMinutes, BigDecimal jitter, Instant now) {
        if (mode != null) this.mode = mode; if (targetValue != null || "NORMAL".equals(mode) || "STALE".equals(mode)) this.targetValue = targetValue;
        if (durationMinutes != null) { this.durationMinutes = durationMinutes; this.endsAt = now.plus(Duration.ofMinutes(durationMinutes)); }
        if (jitter != null) this.jitter = jitter; this.status = "ACTIVE"; this.nextEmitAt = now; this.updatedAt = now;
    }
    public void stop(Instant now) { status = "STOPPED"; updatedAt = now; }
    public void emitted(Instant at, Duration interval) { lastEmitAt = at; nextEmitAt = at.plus(interval); if (!nextEmitAt.isBefore(endsAt)) status = "COMPLETED"; updatedAt = at; }
    public void complete(Instant now) { status = "COMPLETED"; updatedAt = now; }
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getDeviceId() { return deviceId; }
    public UUID getSensorId() { return sensorId; }
    public String getMode() { return mode; }
    public BigDecimal getTargetValue() { return targetValue; }
    public int getDurationMinutes() { return durationMinutes; }
    public BigDecimal getJitter() { return jitter; }
    public String getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndsAt() { return endsAt; }
    public Instant getNextEmitAt() { return nextEmitAt; }
    public Instant getLastEmitAt() { return lastEmitAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
