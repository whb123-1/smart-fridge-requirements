package com.xianzhi.fridge.fridge.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, UUID> {
    List<Device> findByUserIdAndZoneIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID userId, UUID zoneId);
    Optional<Device> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);
    Optional<Device> findByMqttUsernameAndDeletedAtIsNull(String mqttUsername);
    Optional<Device> findByMqttClientIdAndDeletedAtIsNull(String mqttClientId);
}
