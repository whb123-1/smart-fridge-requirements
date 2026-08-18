package com.xianzhi.fridge.fridge.infrastructure;

import com.xianzhi.fridge.fridge.domain.SensorMetric;
import com.xianzhi.fridge.fridge.domain.ZoneKind;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SensorProfileRepository extends JpaRepository<SensorProfile, UUID> {
    Optional<SensorProfile> findFirstByZoneKindAndMetricOrderByProfileVersionDesc(ZoneKind zoneKind, SensorMetric metric);
}
