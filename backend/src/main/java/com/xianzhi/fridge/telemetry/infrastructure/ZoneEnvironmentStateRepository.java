package com.xianzhi.fridge.telemetry.infrastructure;

import com.xianzhi.fridge.fridge.domain.SensorMetric;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneEnvironmentStateRepository extends JpaRepository<ZoneEnvironmentState, UUID> {
    Optional<ZoneEnvironmentState> findByZoneIdAndMetric(UUID zoneId, SensorMetric metric);
    List<ZoneEnvironmentState> findByFridgeIdOrderByZoneIdAscMetricAsc(UUID fridgeId);
}
