package com.xianzhi.fridge.notification.infrastructure;

import java.time.Instant;import java.util.List;import java.util.UUID;import org.springframework.data.jpa.repository.JpaRepository;
public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery,UUID>{List<NotificationDelivery> findTop100ByStatusAndAvailableAtBeforeOrderByAvailableAtAsc(String status,Instant now);}
