package com.xianzhi.fridge.telemetry.infrastructure;

import com.xianzhi.fridge.fridge.domain.SensorMetric;
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
@Table(name = "zone_environment_state")
public class ZoneEnvironmentState {
    @Id @JdbcTypeCode(Types.BINARY) private UUID id;
    @JdbcTypeCode(Types.BINARY) @Column(name = "user_id", nullable = false) private UUID userId;
    @JdbcTypeCode(Types.BINARY) @Column(name = "fridge_id", nullable = false) private UUID fridgeId;
    @JdbcTypeCode(Types.BINARY) @Column(name = "zone_id", nullable = false) private UUID zoneId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private SensorMetric metric;
    @Column(name = "current_value", precision = 10, scale = 3) private BigDecimal currentValue;
    @Column(name = "current_unit", nullable = false, length = 16) private String currentUnit;
    @Column(name = "current_quality", nullable = false, length = 16) private String currentQuality;
    @Column(name = "last_observed_at") private Instant lastObservedAt;
    @Column(name = "last_received_at") private Instant lastReceivedAt;
    @Column(name = "outside_since") private Instant outsideSince;
    @Column(name = "normal_since") private Instant normalSince;
    @Column(name = "stale_since") private Instant staleSince;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    protected ZoneEnvironmentState() { }
    public ZoneEnvironmentState(UUID id, UUID userId, UUID fridgeId, UUID zoneId, SensorMetric metric, Instant now) {
        this.id = id; this.userId = userId; this.fridgeId = fridgeId; this.zoneId = zoneId; this.metric = metric;
        this.currentUnit = metric == SensorMetric.TEMPERATURE ? "C" : "PERCENT";
        this.currentQuality = "NO_SENSOR"; this.updatedAt = now;
    }
    public void noSensor(Instant now) {
        currentQuality = "NO_SENSOR"; currentValue = null; lastObservedAt = null; lastReceivedAt = null;
        outsideSince = null; normalSince = null; staleSince = null; updatedAt = now;
    }
    public void stale(Instant since, Instant now) {
        currentQuality = "STALE"; outsideSince = null; normalSince = null;
        if (staleSince == null) staleSince = since; updatedAt = now;
    }
    public void measured(BigDecimal value, Instant observedAt, Instant receivedAt, boolean outside, Instant now) {
        currentValue = value; currentQuality = "GOOD"; lastObservedAt = observedAt; lastReceivedAt = receivedAt;
        staleSince = null;
        if (outside) { if (outsideSince == null) outsideSince = now; normalSince = null; }
        else { if (normalSince == null) normalSince = now; outsideSince = null; }
        updatedAt = now;
    }
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getFridgeId() { return fridgeId; }
    public UUID getZoneId() { return zoneId; }
    public SensorMetric getMetric() { return metric; }
    public BigDecimal getCurrentValue() { return currentValue; }
    public String getCurrentUnit() { return currentUnit; }
    public String getCurrentQuality() { return currentQuality; }
    public Instant getLastObservedAt() { return lastObservedAt; }
    public Instant getLastReceivedAt() { return lastReceivedAt; }
    public Instant getOutsideSince() { return outsideSince; }
    public Instant getNormalSince() { return normalSince; }
    public Instant getStaleSince() { return staleSince; }
    public Instant getUpdatedAt() { return updatedAt; }
}
