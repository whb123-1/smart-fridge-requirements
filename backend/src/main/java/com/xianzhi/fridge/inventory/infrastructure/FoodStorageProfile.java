package com.xianzhi.fridge.inventory.infrastructure;

import com.xianzhi.fridge.inventory.domain.FoodCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.sql.Types;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "food_storage_profile")
public class FoodStorageProfile {
    @Id @JdbcTypeCode(Types.BINARY) private UUID id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private FoodCategory category;
    @Column(name = "zone_kind", length = 24) private String zoneKind;
    @Column(name = "profile_version", nullable = false) private int profileVersion;
    @Column(name = "unopened_hours") private Integer unopenedHours;
    @Column(name = "opened_hours") private Integer openedHours;
    @Column(name = "risk_coefficient", nullable = false, precision = 8, scale = 3) private BigDecimal riskCoefficient;
    protected FoodStorageProfile() { }
    public FoodCategory getCategory() { return category; }
    public String getZoneKind() { return zoneKind; }
    public int getProfileVersion() { return profileVersion; }
    public Integer getUnopenedHours() { return unopenedHours; }
    public Integer getOpenedHours() { return openedHours; }
}
