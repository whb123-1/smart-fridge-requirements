package com.xianzhi.fridge.telemetry.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelemetryMessageRepository extends JpaRepository<TelemetryMessage, UUID> {
    boolean existsByDeviceIdAndMessageId(UUID deviceId, String messageId);
}
