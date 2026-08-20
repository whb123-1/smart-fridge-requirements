package com.xianzhi.fridge.inventory.infrastructure;

import com.xianzhi.fridge.inventory.domain.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "inventory_transaction")
public class InventoryTransaction {
    @Id @JdbcTypeCode(Types.BINARY) private UUID id;
    @JdbcTypeCode(Types.BINARY) @Column(name = "batch_id", nullable = false) private UUID batchId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private TransactionType type;
    @Column(name = "before_quantity", nullable = false, precision = 14, scale = 3) private BigDecimal beforeQuantity;
    @Column(name = "after_quantity", nullable = false, precision = 14, scale = 3) private BigDecimal afterQuantity;
    @Column(name = "quantity_delta", nullable = false, precision = 14, scale = 3) private BigDecimal quantityDelta;
    @Column(nullable = false, length = 24) private String unit;
    @Column(name = "source_type", length = 40) private String sourceType;
    @JdbcTypeCode(Types.BINARY) @Column(name = "source_id") private UUID sourceId;
    @JdbcTypeCode(Types.BINARY) @Column(name = "actor_user_id", nullable = false) private UUID actorUserId;
    @Column(name = "idempotency_key", length = 128) private String idempotencyKey;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "deleted_at") private Instant deletedAt;
    protected InventoryTransaction() { }
    public InventoryTransaction(UUID id, UUID batchId, TransactionType type, BigDecimal beforeQuantity,
                                BigDecimal afterQuantity, BigDecimal quantityDelta, String unit,
                                String sourceType, UUID sourceId, UUID actorUserId, String idempotencyKey) {
        this.id = id; this.batchId = batchId; this.type = type; this.beforeQuantity = beforeQuantity;
        this.afterQuantity = afterQuantity; this.quantityDelta = quantityDelta; this.unit = unit;
        this.sourceType = sourceType; this.sourceId = sourceId; this.actorUserId = actorUserId; this.idempotencyKey = idempotencyKey;
        this.createdAt = Instant.now();
    }
    public UUID getId() { return id; }
    public Instant getDeletedAt() { return deletedAt; }
}
