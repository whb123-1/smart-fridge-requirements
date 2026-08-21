package com.xianzhi.fridge.inventory.infrastructure;

import com.xianzhi.fridge.inventory.domain.FoodCategory;
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
@Table(name = "inventory_item")
public class InventoryItem {
    @Id @JdbcTypeCode(Types.BINARY) private UUID id;
    @JdbcTypeCode(Types.BINARY) @Column(name = "user_id", nullable = false) private UUID userId;
    @JdbcTypeCode(Types.BINARY) @Column(name = "fridge_id", nullable = false) private UUID fridgeId;
    @JdbcTypeCode(Types.BINARY) @Column(name = "catalog_id") private UUID catalogId;
    @Column(name = "display_name", nullable = false, length = 120) private String displayName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private FoodCategory category;
    @Column(name = "low_stock_quantity", precision = 14, scale = 3) private BigDecimal lowStockQuantity;
    @Column(name = "default_unit", nullable = false, length = 24) private String defaultUnit;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "deleted_at") private Instant deletedAt;
    @Version private long version;
    protected InventoryItem() { }
    public InventoryItem(UUID id, UUID userId, UUID fridgeId, UUID catalogId, String displayName,
                         FoodCategory category, BigDecimal lowStockQuantity, String defaultUnit) {
        this.id = id; this.userId = userId; this.fridgeId = fridgeId; this.catalogId = catalogId;
        this.displayName = displayName; this.category = category; this.lowStockQuantity = lowStockQuantity;
        this.defaultUnit = defaultUnit;
    }
    @PrePersist void onCreate() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getFridgeId() { return fridgeId; }
    public UUID getCatalogId() { return catalogId; }
    public String getDisplayName() { return displayName; }
    public FoodCategory getCategory() { return category; }
    public BigDecimal getLowStockQuantity() { return lowStockQuantity; }
    public String getDefaultUnit() { return defaultUnit; }
    public Instant getDeletedAt() { return deletedAt; }
    public void update(String name, FoodCategory category, BigDecimal lowStockQuantity, String defaultUnit) {
        this.displayName = name; this.category = category; this.lowStockQuantity = lowStockQuantity; this.defaultUnit = defaultUnit;
    }
    public void update(String name, UUID catalogId, FoodCategory category, BigDecimal lowStockQuantity, String defaultUnit) {
        this.displayName = name; this.catalogId = catalogId; this.category = category; this.lowStockQuantity = lowStockQuantity; this.defaultUnit = defaultUnit;
    }
    public void softDelete(Instant at) { deletedAt = at; }
}
