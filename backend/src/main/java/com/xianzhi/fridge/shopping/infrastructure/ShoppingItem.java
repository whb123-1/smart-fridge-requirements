package com.xianzhi.fridge.shopping.infrastructure;

import com.xianzhi.fridge.shopping.domain.ShoppingStatus;
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
@Table(name = "shopping_item")
public class ShoppingItem {
    @Id @JdbcTypeCode(Types.BINARY) private UUID id;
    @JdbcTypeCode(Types.BINARY) @Column(name = "list_id", nullable = false) private UUID listId;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, length = 24) private String category;
    @Column(precision = 14, scale = 3) private BigDecimal quantity;
    @Column(length = 24) private String unit;
    @Column(length = 255) private String note;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private ShoppingStatus status;
    @Column(name = "source_type", nullable = false, length = 32) private String sourceType;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;
    protected ShoppingItem() { }
    public ShoppingItem(UUID id, UUID listId, String name, String category, BigDecimal quantity, String unit,
                         String note, ShoppingStatus status, String sourceType) {
        this.id = id; this.listId = listId; this.name = name; this.category = category;
        this.quantity = quantity; this.unit = unit; this.note = note; this.status = status; this.sourceType = sourceType;
    }
    @PrePersist void onCreate() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
    public UUID getId() { return id; }
    public UUID getListId() { return listId; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public BigDecimal getQuantity() { return quantity; }
    public String getUnit() { return unit; }
    public String getNote() { return note; }
    public ShoppingStatus getStatus() { return status; }
    public String getSourceType() { return sourceType; }
    public void update(String name, String category, BigDecimal quantity, String unit, String note, ShoppingStatus status) {
        this.name = name; this.category = category; this.quantity = quantity; this.unit = unit; this.note = note; this.status = status;
    }
}
