package com.xianzhi.fridge.inventory.infrastructure;

import com.xianzhi.fridge.inventory.domain.BatchStatus;
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
@Table(name = "inventory_batch")
public class InventoryBatch {
    @Id @JdbcTypeCode(Types.BINARY) private UUID id;
    @JdbcTypeCode(Types.BINARY) @Column(name = "item_id", nullable = false) private UUID itemId;
    @Column(name = "input_name", length = 120) private String inputName;
    @JdbcTypeCode(Types.BINARY) @Column(name = "zone_id") private UUID zoneId;
    @Column(name = "stored_at", nullable = false) private Instant storedAt;
    @Column(name = "opened_at") private Instant openedAt;
    @Column(name = "package_expires_at") private Instant packageExpiresAt;
    @Column(name = "shelf_life_days") private Integer shelfLifeDays;
    @Column(name = "initial_quantity", nullable = false, precision = 14, scale = 3) private BigDecimal initialQuantity;
    @Column(name = "remaining_quantity", nullable = false, precision = 14, scale = 3) private BigDecimal remainingQuantity;
    @Column(nullable = false, length = 24) private String unit;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private BatchStatus status;
    @Column(name = "remind_at") private Instant remindAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;
    protected InventoryBatch() { }
    public InventoryBatch(UUID id, UUID itemId, UUID zoneId, Instant storedAt, Instant openedAt,
                          Instant packageExpiresAt, Integer shelfLifeDays, BigDecimal quantity, String unit, Instant remindAt) {
        this(id, itemId, null, zoneId, storedAt, openedAt, packageExpiresAt, shelfLifeDays, quantity, unit, remindAt);
    }
    public InventoryBatch(UUID id, UUID itemId, String inputName, UUID zoneId, Instant storedAt, Instant openedAt,
                          Instant packageExpiresAt, Integer shelfLifeDays, BigDecimal quantity, String unit, Instant remindAt) {
        this.id = id; this.itemId = itemId; this.zoneId = zoneId; this.storedAt = storedAt;
        this.inputName = inputName;
        this.openedAt = openedAt; this.packageExpiresAt = packageExpiresAt; this.shelfLifeDays = shelfLifeDays;
        this.initialQuantity = quantity; this.remainingQuantity = quantity; this.unit = unit;
        this.status = quantity.signum() == 0 ? BatchStatus.DEPLETED : BatchStatus.ACTIVE; this.remindAt = remindAt;
    }
    @PrePersist void onCreate() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
    public UUID getId() { return id; }
    public UUID getItemId() { return itemId; }
    public String getInputName() { return inputName; }
    public UUID getZoneId() { return zoneId; }
    public Instant getStoredAt() { return storedAt; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getPackageExpiresAt() { return packageExpiresAt; }
    public Integer getShelfLifeDays() { return shelfLifeDays; }
    public BigDecimal getInitialQuantity() { return initialQuantity; }
    public BigDecimal getRemainingQuantity() { return remainingQuantity; }
    public String getUnit() { return unit; }
    public BatchStatus getStatus() { return status; }
    public Instant getRemindAt() { return remindAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void moveToItem(UUID targetItemId) { this.itemId = targetItemId; }
    public void updateSchedule(UUID zoneId, Instant openedAt, Instant packageExpiresAt, Integer shelfLifeDays, Instant remindAt) {
        this.zoneId = zoneId; this.openedAt = openedAt; this.packageExpiresAt = packageExpiresAt; this.shelfLifeDays = shelfLifeDays; this.remindAt = remindAt;
    }
    public void adjustTo(BigDecimal target) { remainingQuantity = target; status = target.signum() == 0 ? BatchStatus.DEPLETED : BatchStatus.ACTIVE; }
    public void consume(BigDecimal quantity, BatchStatus terminalStatus) {
        remainingQuantity = remainingQuantity.subtract(quantity);
        status = remainingQuantity.signum() == 0 ? terminalStatus : BatchStatus.ACTIVE;
    }
}
