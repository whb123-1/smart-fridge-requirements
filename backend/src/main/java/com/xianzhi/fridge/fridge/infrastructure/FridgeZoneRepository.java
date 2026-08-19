package com.xianzhi.fridge.fridge.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FridgeZoneRepository extends JpaRepository<FridgeZone, UUID> {
    List<FridgeZone> findByFridgeIdInAndDeletedAtIsNullOrderByCreatedAtAsc(Collection<UUID> fridgeIds);
    List<FridgeZone> findByFridgeIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID fridgeId);
    List<FridgeZone> findByEnabledTrueAndDeletedAtIsNullOrderByCreatedAtAsc();
}
