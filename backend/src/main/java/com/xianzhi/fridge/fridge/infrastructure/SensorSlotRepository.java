package com.xianzhi.fridge.fridge.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SensorSlotRepository extends JpaRepository<SensorSlot, UUID> {
    List<SensorSlot> findByZoneIdIn(Collection<UUID> zoneIds);
    List<SensorSlot> findByDeviceIdAndEnabledTrueOrderBySlotIndexAsc(UUID deviceId);
    List<SensorSlot> findByZoneIdAndEnabledTrueOrderBySlotIndexAsc(UUID zoneId);
    Optional<SensorSlot> findByIdAndDeviceId(UUID id, UUID deviceId);
}
