package com.xianzhi.fridge.inventory.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {
    List<InventoryItem> findByUserIdAndFridgeIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId, UUID fridgeId);
    List<InventoryItem> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);
    Optional<InventoryItem> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);
    Optional<InventoryItem> findByIdAndUserId(UUID id, UUID userId);
    Optional<InventoryItem> findFirstByUserIdAndFridgeIdAndCatalogIdAndDeletedAtIsNull(UUID userId, UUID fridgeId, UUID catalogId);
}
