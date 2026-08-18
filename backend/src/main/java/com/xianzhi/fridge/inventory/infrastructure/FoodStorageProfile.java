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
    @Column(name = "temperature_moderate_deviation_c", nullable = false, precision = 8, scale = 3) private BigDecimal temperatureModerateDeviationC;
    @Column(name = "temperature_severe_deviation_c", nullable = false, precision = 8, scale = 3) private BigDecimal temperatureSevereDeviationC;
    @Column(name = "humidity_moderate_deviation_pct", nullable = false, precision = 8, scale = 3) private BigDecimal humidityModerateDeviationPct;
    @Column(name = "humidity_severe_deviation_pct", nullable = false, precision = 8, scale = 3) private BigDecimal humiditySevereDeviationPct;
    @Column(name = "mild_risk_multiplier", nullable = false, precision = 8, scale = 3) private BigDecimal mildRiskMultiplier;
    @Column(name = "moderate_risk_multiplier", nullable = false, precision = 8, scale = 3) private BigDecimal moderateRiskMultiplier;
    @Column(name = "severe_risk_multiplier", nullable = false, precision = 8, scale = 3) private BigDecimal severeRiskMultiplier;
    @Column(name = "high_risk_minutes", nullable = false, precision = 14, scale = 3) private BigDecimal highRiskMinutes;
    protected FoodStorageProfile() { }
    public FoodCategory getCategory() { return category; }
    public String getZoneKind() { return zoneKind; }
    public int getProfileVersion() { return profileVersion; }
    public Integer getUnopenedHours() { return unopenedHours; }
    public Integer getOpenedHours() { return openedHours; }
    public BigDecimal getRiskCoefficient() { return riskCoefficient; }
    public BigDecimal getTemperatureModerateDeviationC() { return temperatureModerateDeviationC; }
    public BigDecimal getTemperatureSevereDeviationC() { return temperatureSevereDeviationC; }
    public BigDecimal getHumidityModerateDeviationPct() { return humidityModerateDeviationPct; }
    public BigDecimal getHumiditySevereDeviationPct() { return humiditySevereDeviationPct; }
    public BigDecimal getMildRiskMultiplier() { return mildRiskMultiplier; }
    public BigDecimal getModerateRiskMultiplier() { return moderateRiskMultiplier; }
    public BigDecimal getSevereRiskMultiplier() { return severeRiskMultiplier; }
    public BigDecimal getHighRiskMinutes() { return highRiskMinutes; }
}
