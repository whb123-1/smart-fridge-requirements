package com.xianzhi.fridge.inventory.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.sql.Types;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "food_weight_estimate")
public class FoodWeightEstimate {
    @Id @JdbcTypeCode(Types.BINARY) private UUID id;
    @JdbcTypeCode(Types.BINARY) @Column(name = "catalog_id", nullable = false) private UUID catalogId;
    @Column(nullable = false, length = 80) private String label;
    @Column(name = "reference_grams", nullable = false, precision = 12, scale = 3) private BigDecimal referenceGrams;
    @Column(nullable = false, length = 24) private String unit;
    @Column(nullable = false, length = 120) private String source;
    protected FoodWeightEstimate() { }
    public UUID getId() { return id; }
    public UUID getCatalogId() { return catalogId; }
    public String getLabel() { return label; }
    public BigDecimal getReferenceGrams() { return referenceGrams; }
    public String getUnit() { return unit; }
    public String getSource() { return source; }
}
