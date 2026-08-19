package com.xianzhi.fridge.fridge.infrastructure;

import com.xianzhi.fridge.fridge.domain.DeviceStatus;
import com.xianzhi.fridge.fridge.domain.DeviceType;
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
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "device")
public class Device {
    @Id @JdbcTypeCode(Types.BINARY) private UUID id;
    @JdbcTypeCode(Types.BINARY) @Column(name = "user_id", nullable = false) private UUID userId;
    @JdbcTypeCode(Types.BINARY) @Column(name = "zone_id", nullable = false) private UUID zoneId;
    @Column(nullable = false, length = 96) private String name;
    @Enumerated(EnumType.STRING) @Column(name = "device_type", nullable = false, length = 24) private DeviceType type;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private DeviceStatus status;
    @Column(name = "firmware_version", length = 64) private String firmwareVersion;
    @Column(name = "last_seen_at") private Instant lastSeenAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "deleted_at") private Instant deletedAt;
    @Version private long version;

    protected Device() { }
    public Device(UUID id, UUID userId, UUID zoneId, String name, DeviceType type) {
        this.id = id; this.userId = userId; this.zoneId = zoneId; this.name = name; this.type = type;
        this.status = DeviceStatus.ACTIVE;
    }
    @PrePersist void onCreate() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getZoneId() { return zoneId; }
    public String getName() { return name; }
    public DeviceType getType() { return type; }
    public DeviceStatus getStatus() { return status; }
    public String getFirmwareVersion() { return firmwareVersion; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public void seen(Instant at, String firmwareVersion) { this.lastSeenAt = at; if (firmwareVersion != null && !firmwareVersion.isBlank()) this.firmwareVersion = firmwareVersion; }
    public void disable(Instant at) { this.status = DeviceStatus.DISABLED; this.deletedAt = at; }
}
