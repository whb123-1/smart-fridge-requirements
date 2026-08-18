package com.xianzhi.fridge.inventory.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, UUID> {
    List<InventoryTransaction> findByBatchIdOrderByCreatedAtDesc(UUID batchId);
}
