package com.xianzhi.fridge.fridge.infrastructure;

import com.xianzhi.fridge.fridge.domain.ZoneDefaults;
import com.xianzhi.fridge.fridge.domain.ZoneKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "fridge_zone")
public class FridgeZone {
    @Id @JdbcTypeCode(Types.BINARY) private UUID id;
    @JdbcTypeCode(Types.BINARY) @Column(name = "fridge_id", nullable = false) private UUID fridgeId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private ZoneKind kind;
    @Column(nullable = false, length = 48) private String name;
    @Column(nullable = false) private boolean enabled;
    @Column(name = "target_temperature_c", nullable = false, precision = 5, scale = 2) private BigDecimal targetTemperatureC;
    @Column(name = "target_humidity_pct", nullable = false, precision = 5, scale = 2) private BigDecimal targetHumidityPct;
    @Column(name = "safe_temperature_min_c", nullable = false, precision = 5, scale = 2) private BigDecimal safeTemperatureMinC;
    @Column(name = "safe_temperature_max_c", nullable = false, precision = 5, scale = 2) private BigDecimal safeTemperatureMaxC;
    @Column(name = "safe_humidity_min_pct", nullable = false, precision = 5, scale = 2) private BigDecimal safeHumidityMinPct;
    @Column(name = "safe_humidity_max_pct", nullable = false, precision = 5, scale = 2) private BigDecimal safeHumidityMaxPct;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "deleted_at") private Instant deletedAt;
    @Version private long version;
    protected FridgeZone() { }
    public FridgeZone(UUID id, UUID fridgeId, ZoneKind kind, String name, ZoneDefaults defaults) {
        this.id = id; this.fridgeId = fridgeId; this.kind = kind; this.name = name; this.enabled = true;
        this.targetTemperatureC = defaults.targetTemperatureC(); this.targetHumidityPct = defaults.targetHumidityPct();
        this.safeTemperatureMinC = defaults.safeTemperatureMinC(); this.safeTemperatureMaxC = defaults.safeTemperatureMaxC();
        this.safeHumidityMinPct = defaults.safeHumidityMinPct(); this.safeHumidityMaxPct = defaults.safeHumidityMaxPct();
    }
    @PrePersist void onCreate() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
    public UUID getId() { return id; }
    public UUID getFridgeId() { return fridgeId; }
    public ZoneKind getKind() { return kind; }
    public String getName() { return name; }
    public boolean isEnabled() { return enabled; }
    public BigDecimal getTargetTemperatureC() { return targetTemperatureC; }
    public BigDecimal getTargetHumidityPct() { return targetHumidityPct; }
    public BigDecimal getSafeTemperatureMinC() { return safeTemperatureMinC; }
    public BigDecimal getSafeTemperatureMaxC() { return safeTemperatureMaxC; }
    public BigDecimal getSafeHumidityMinPct() { return safeHumidityMinPct; }
    public BigDecimal getSafeHumidityMaxPct() { return safeHumidityMaxPct; }
    public Instant getDeletedAt() { return deletedAt; }

    public void updateSettings(String name, BigDecimal targetTemperatureC, BigDecimal targetHumidityPct) {
        this.name = name;
        this.targetTemperatureC = targetTemperatureC;
        this.targetHumidityPct = targetHumidityPct;
    }
}
