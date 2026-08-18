package com.xianzhi.fridge.inventory.infrastructure;

import com.xianzhi.fridge.inventory.domain.FoodCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.sql.Types;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "food_catalog")
public class FoodCatalog {
    @Id @JdbcTypeCode(Types.BINARY) private UUID id;
    @Column(name = "canonical_name", nullable = false, length = 120) private String canonicalName;
    @Column(length = 512) private String aliases;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private FoodCategory category;
    @Column(name = "default_unit", nullable = false, length = 24) private String defaultUnit;
    @Column(name = "default_shelf_life_days") private Integer defaultShelfLifeDays;
    protected FoodCatalog() { }
    public UUID getId() { return id; }
    public String getCanonicalName() { return canonicalName; }
    public String getAliases() { return aliases; }
    public FoodCategory getCategory() { return category; }
    public String getDefaultUnit() { return defaultUnit; }
    public Integer getDefaultShelfLifeDays() { return defaultShelfLifeDays; }
}
