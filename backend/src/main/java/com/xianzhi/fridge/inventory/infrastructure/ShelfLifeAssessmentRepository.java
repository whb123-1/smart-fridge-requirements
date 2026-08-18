package com.xianzhi.fridge.inventory.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShelfLifeAssessmentRepository extends JpaRepository<ShelfLifeAssessment, UUID> {
    Optional<ShelfLifeAssessment> findFirstByBatchIdOrderByCalculatedAtDesc(UUID batchId);
    List<ShelfLifeAssessment> findByBatchIdInOrderByCalculatedAtDesc(List<UUID> batchIds);
}
