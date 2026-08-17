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
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "sensor")
public class SensorSlot {
    @Id @JdbcTypeCode(Types.BINARY) private UUID id;
    @JdbcTypeCode(Types.BINARY) @Column(name = "zone_id", nullable = false) private UUID zoneId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private SensorMetric metric;
    @Column(name = "binding_status", nullable = false, length = 24) private String bindingStatus;
    @Column(nullable = false, length = 24) private String source;
    @Column(name = "slot_index", nullable = false) private int slotIndex;
    @Column(nullable = false) private boolean enabled;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected SensorSlot() { }
    public SensorSlot(UUID id, UUID zoneId, SensorMetric metric, int slotIndex) {
        this.id = id; this.zoneId = zoneId; this.metric = metric; this.slotIndex = slotIndex;
        this.bindingStatus = "PENDING_BIND"; this.source = "LOGICAL_SLOT"; this.enabled = true;
    }
    @PrePersist void onCreate() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
    public UUID getZoneId() { return zoneId; }
    public SensorMetric getMetric() { return metric; }
    public String getBindingStatus() { return bindingStatus; }
}
