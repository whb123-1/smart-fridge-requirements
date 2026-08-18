package com.xianzhi.fridge.telemetry.infrastructure;

import com.xianzhi.fridge.fridge.domain.SensorMetric;
import com.xianzhi.fridge.telemetry.domain.IncidentReason;
import com.xianzhi.fridge.telemetry.domain.IncidentSeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "environment_incident")
public class EnvironmentIncident {
    @Id @JdbcTypeCode(Types.BINARY) private UUID id;
    @JdbcTypeCode(Types.BINARY) @Column(name = "user_id", nullable = false) private UUID userId;
    @JdbcTypeCode(Types.BINARY) @Column(name = "fridge_id", nullable = false) private UUID fridgeId;
    @JdbcTypeCode(Types.BINARY) @Column(name = "zone_id", nullable = false) private UUID zoneId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private SensorMetric metric;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private IncidentReason reason;
    @Column(nullable = false, length = 16) private String direction;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private IncidentSeverity severity;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "last_observed_at", nullable = false) private Instant lastObservedAt;
    @Column(name = "ended_at") private Instant endedAt;
    @Column(name = "max_deviation", nullable = false, precision = 10, scale = 3) private BigDecimal maxDeviation;
    @Column(nullable = false, length = 16) private String status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    protected EnvironmentIncident() { }
    public EnvironmentIncident(UUID id, UUID userId, UUID fridgeId, UUID zoneId, SensorMetric metric,
                               IncidentReason reason, String direction, IncidentSeverity severity,
                               Instant startedAt, Instant observedAt, BigDecimal deviation, Instant now) {
        this.id = id; this.userId = userId; this.fridgeId = fridgeId; this.zoneId = zoneId; this.metric = metric;
        this.reason = reason; this.direction = direction; this.severity = severity; this.startedAt = startedAt;
        this.lastObservedAt = observedAt; this.maxDeviation = deviation; this.status = "OPEN";
        this.createdAt = this.updatedAt = now;
    }
    public void observe(String direction, IncidentSeverity severity, BigDecimal deviation, Instant observedAt, Instant now) {
        this.direction = direction; if (severity.ordinal() > this.severity.ordinal()) this.severity = severity;
        if (deviation.compareTo(maxDeviation) > 0) maxDeviation = deviation;
        lastObservedAt = observedAt; updatedAt = now;
    }
    public void close(Instant at) { status = "CLOSED"; endedAt = at; updatedAt = at; }
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getFridgeId() { return fridgeId; }
    public UUID getZoneId() { return zoneId; }
    public SensorMetric getMetric() { return metric; }
    public IncidentReason getReason() { return reason; }
    public String getDirection() { return direction; }
    public IncidentSeverity getSeverity() { return severity; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getLastObservedAt() { return lastObservedAt; }
    public Instant getEndedAt() { return endedAt; }
    public BigDecimal getMaxDeviation() { return maxDeviation; }
    public String getStatus() { return status; }
}
