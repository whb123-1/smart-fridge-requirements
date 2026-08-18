package com.xianzhi.fridge.telemetry.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BatchEnvironmentExposureRepository extends JpaRepository<BatchEnvironmentExposure, UUID> {
    Optional<BatchEnvironmentExposure> findByBatchIdAndIncidentId(UUID batchId, UUID incidentId);
    List<BatchEnvironmentExposure> findByBatchId(UUID batchId);
}
