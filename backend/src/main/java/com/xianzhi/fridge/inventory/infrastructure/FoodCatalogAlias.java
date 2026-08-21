package com.xianzhi.fridge.inventory.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "food_catalog_alias")
public class FoodCatalogAlias {
    @Id @JdbcTypeCode(Types.BINARY) private UUID id;
    @JdbcTypeCode(Types.BINARY) @Column(name = "catalog_id", nullable = false) private UUID catalogId;
    @Column(nullable = false, length = 120) private String alias;
    @Column(name = "normalized_alias", nullable = false, length = 120) private String normalizedAlias;
    @Column(nullable = false, length = 32) private String source;
    @Column(nullable = false, precision = 5, scale = 4) private BigDecimal confidence;
    @Column(nullable = false) private boolean approved;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected FoodCatalogAlias() { }
    public UUID getCatalogId() { return catalogId; }
    public String getAlias() { return alias; }
    public String getNormalizedAlias() { return normalizedAlias; }
    public boolean isApproved() { return approved; }
}
