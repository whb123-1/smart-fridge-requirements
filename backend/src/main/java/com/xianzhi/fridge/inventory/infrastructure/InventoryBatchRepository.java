package com.xianzhi.fridge.inventory.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.xianzhi.fridge.inventory.domain.BatchStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface InventoryBatchRepository extends JpaRepository<InventoryBatch, UUID> {
    List<InventoryBatch> findByItemIdInOrderByStoredAtDesc(Collection<UUID> itemIds);
    List<InventoryBatch> findByItemIdOrderByStoredAtDesc(UUID itemId);
    List<InventoryBatch> findByZoneId(UUID zoneId);
    List<InventoryBatch> findByZoneIdAndStatus(UUID zoneId, BatchStatus status);
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<InventoryBatch> findById(UUID id);

    @Query("select batch from InventoryBatch batch where batch.id = :id")
    Optional<InventoryBatch> findForRead(@Param("id") UUID id);
}
