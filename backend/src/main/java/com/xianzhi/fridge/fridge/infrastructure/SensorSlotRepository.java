package com.xianzhi.fridge.fridge.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SensorSlotRepository extends JpaRepository<SensorSlot, UUID> {
    List<SensorSlot> findByZoneIdIn(Collection<UUID> zoneIds);
    List<SensorSlot> findByDeviceIdAndEnabledTrueOrderBySlotIndexAsc(UUID deviceId);
    List<SensorSlot> findByZoneIdAndEnabledTrueOrderBySlotIndexAsc(UUID zoneId);
    Optional<SensorSlot> findByIdAndDeviceId(UUID id, UUID deviceId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sensor from SensorSlot sensor where sensor.id = :id")
    Optional<SensorSlot> findByIdForUpdate(@Param("id") UUID id);
}
