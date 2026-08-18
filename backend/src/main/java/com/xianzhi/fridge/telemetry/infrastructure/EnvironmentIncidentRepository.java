package com.xianzhi.fridge.telemetry.infrastructure;

import com.xianzhi.fridge.fridge.domain.SensorMetric;
import com.xianzhi.fridge.telemetry.domain.IncidentReason;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvironmentIncidentRepository extends JpaRepository<EnvironmentIncident, UUID> {
    Optional<EnvironmentIncident> findByZoneIdAndMetricAndReasonAndStatus(UUID zoneId, SensorMetric metric, IncidentReason reason, String status);
    List<EnvironmentIncident> findByFridgeIdAndStatusOrderByStartedAtDesc(UUID fridgeId, String status);
    List<EnvironmentIncident> findByStatus(String status);
    List<EnvironmentIncident> findByReasonOrderByStartedAtAsc(IncidentReason reason);
}
