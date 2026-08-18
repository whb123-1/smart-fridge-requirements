package com.xianzhi.fridge.fridge.infrastructure;

import com.xianzhi.fridge.fridge.domain.SensorMetric;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.sql.Types;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "sensor")
public class SensorSlot {
    @Id @JdbcTypeCode(Types.BINARY) private UUID id;
    @JdbcTypeCode(Types.BINARY) @Column(name = "zone_id", nullable = false) private UUID zoneId;
    @JdbcTypeCode(Types.BINARY) @Column(name = "device_id") private UUID deviceId;
    @JdbcTypeCode(Types.BINARY) @Column(name = "profile_id") private UUID profileId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private SensorMetric metric;
    @Column(length = 96) private String name;
    @Column(name = "external_key", length = 96) private String externalKey;
    @Column(name = "binding_status", nullable = false, length = 24) private String bindingStatus;
    @Column(nullable = false, length = 24) private String source;
    @Column(name = "slot_index", nullable = false) private int slotIndex;
    @Column(nullable = false) private boolean enabled;
    @Column(name = "last_valid_value", precision = 10, scale = 3) private BigDecimal lastValue;
    @Column(name = "last_unit", length = 16) private String lastUnit;
    @Column(name = "last_quality", length = 16) private String lastQuality;
    @Column(name = "last_observed_at") private Instant lastObservedAt;
    @Column(name = "last_received_at") private Instant lastReceivedAt;
    @Column(name = "consecutive_suspect_count", nullable = false) private int consecutiveSuspectCount;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;
    protected SensorSlot() { }
    public SensorSlot(UUID id, UUID zoneId, SensorMetric metric, int slotIndex) {
        this.id = id; this.zoneId = zoneId; this.metric = metric; this.slotIndex = slotIndex;
        this.bindingStatus = "PENDING_BIND"; this.source = "LOGICAL_SLOT"; this.enabled = true;
    }
    @PrePersist void onCreate() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
    public UUID getId() { return id; }
    public UUID getZoneId() { return zoneId; }
    public UUID getDeviceId() { return deviceId; }
    public UUID getProfileId() { return profileId; }
    public SensorMetric getMetric() { return metric; }
    public String getName() { return name; }
    public String getExternalKey() { return externalKey; }
    public String getBindingStatus() { return bindingStatus; }
    public int getSlotIndex() { return slotIndex; }
    public boolean isEnabled() { return enabled; }
    public BigDecimal getLastValue() { return lastValue; }
    public String getLastUnit() { return lastUnit; }
    public String getLastQuality() { return lastQuality; }
    public Instant getLastObservedAt() { return lastObservedAt; }
    public Instant getLastReceivedAt() { return lastReceivedAt; }
    public int getConsecutiveSuspectCount() { return consecutiveSuspectCount; }
    public void bind(UUID deviceId, UUID profileId, String name, String externalKey) {
        this.deviceId = deviceId; this.profileId = profileId; this.name = name; this.externalKey = externalKey;
        this.bindingStatus = "BOUND"; this.source = "DEVICE"; this.enabled = true;
    }
    public void unbind() {
        this.deviceId = null; this.profileId = null; this.name = null; this.externalKey = null;
        this.bindingStatus = "PENDING_BIND"; this.source = "LOGICAL_SLOT"; this.lastValue = null;
        this.lastUnit = null; this.lastQuality = null; this.lastObservedAt = null; this.lastReceivedAt = null;
        this.consecutiveSuspectCount = 0;
    }
    public void accept(BigDecimal value, String unit, Instant observedAt, Instant receivedAt) {
        this.lastValue = value; this.lastUnit = unit; this.lastQuality = "GOOD";
        this.lastObservedAt = observedAt; this.lastReceivedAt = receivedAt; this.consecutiveSuspectCount = 0;
    }
    public int suspect() { this.lastQuality = "SUSPECT"; return ++this.consecutiveSuspectCount; }
}
