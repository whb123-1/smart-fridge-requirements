package com.xianzhi.fridge.fridge.infrastructure;

import com.xianzhi.fridge.fridge.domain.SensorMetric;
import com.xianzhi.fridge.fridge.domain.ZoneKind;
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
@Table(name = "sensor_profile")
public class SensorProfile {
    @Id @JdbcTypeCode(Types.BINARY) private UUID id;
    @Column(nullable = false, length = 64) private String code;
    @Column(name = "profile_version", nullable = false) private int profileVersion;
    @Enumerated(EnumType.STRING) @Column(name = "zone_kind", nullable = false, length = 24) private ZoneKind zoneKind;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private SensorMetric metric;
    @Column(name = "physical_min", nullable = false, precision = 10, scale = 3) private BigDecimal physicalMin;
    @Column(name = "physical_max", nullable = false, precision = 10, scale = 3) private BigDecimal physicalMax;
    @Column(name = "normal_min", nullable = false, precision = 10, scale = 3) private BigDecimal normalMin;
    @Column(name = "normal_max", nullable = false, precision = 10, scale = 3) private BigDecimal normalMax;
    @Column(name = "max_change_per_minute", nullable = false, precision = 10, scale = 3) private BigDecimal maxChangePerMinute;
    protected SensorProfile() { }
    public UUID getId() { return id; }
    public ZoneKind getZoneKind() { return zoneKind; }
    public SensorMetric getMetric() { return metric; }
    public BigDecimal getPhysicalMin() { return physicalMin; }
    public BigDecimal getPhysicalMax() { return physicalMax; }
    public BigDecimal getNormalMin() { return normalMin; }
    public BigDecimal getNormalMax() { return normalMax; }
    public BigDecimal getMaxChangePerMinute() { return maxChangePerMinute; }
}
