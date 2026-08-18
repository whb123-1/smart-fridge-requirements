package com.xianzhi.fridge.notification.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference,UUID>{
    List<NotificationPreference> findByUserIdOrderByTypeAsc(UUID userId);
    Optional<NotificationPreference> findByUserIdAndType(UUID userId, NotificationType type);
}
