package com.xianzhi.fridge.telemetry.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DebugTelemetryScenarioRepository extends JpaRepository<DebugTelemetryScenario, UUID> {
    List<DebugTelemetryScenario> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<DebugTelemetryScenario> findByIdAndUserId(UUID id, UUID userId);
    List<DebugTelemetryScenario> findTop100ByStatusAndNextEmitAtLessThanEqualOrderByNextEmitAtAsc(String status, Instant now);
}
