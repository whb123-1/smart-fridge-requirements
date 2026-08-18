package com.xianzhi.fridge.notification.application;

import com.xianzhi.fridge.notification.api.NotificationContracts;
import com.xianzhi.fridge.notification.infrastructure.Notification;
import com.xianzhi.fridge.notification.infrastructure.NotificationRepository;
import com.xianzhi.fridge.notification.infrastructure.NotificationPreferenceRepository;
import com.xianzhi.fridge.notification.infrastructure.NotificationDelivery;
import com.xianzhi.fridge.notification.infrastructure.NotificationDeliveryRepository;
import com.xianzhi.fridge.notification.infrastructure.NotificationType;
import com.xianzhi.fridge.shared.application.IdempotencyService;
import com.xianzhi.fridge.shared.domain.UuidV7;
import com.xianzhi.fridge.shared.web.ApiException;
import com.xianzhi.fridge.telemetry.infrastructure.EnvironmentIncident;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private final NotificationRepository notifications;
    private final IdempotencyService idempotency;
    private final Clock clock;
    private final NotificationPreferenceRepository preferences;
    private final NotificationDeliveryRepository deliveries;
    public NotificationService(NotificationRepository notifications, IdempotencyService idempotency, Clock clock,
                               NotificationPreferenceRepository preferences, NotificationDeliveryRepository deliveries) {
        this.notifications = notifications; this.idempotency = idempotency; this.clock = clock; this.preferences=preferences; this.deliveries=deliveries;
    }
    @Transactional(readOnly = true)
    public List<NotificationContracts.View> list(UUID userId, Boolean unreadOnly) {
        return notifications.findByUserIdOrderByCreatedAtDesc(userId).stream().filter(Notification::isInAppVisible)
                .filter(value -> !Boolean.TRUE.equals(unreadOnly) || value.getReadAt() == null)
                .map(this::view).toList();
    }
    @Transactional
    public NotificationContracts.View update(UUID userId, UUID id, String key, NotificationContracts.UpdateRequest request) {
        String path = "/api/v1/notifications/" + id;
        NotificationContracts.View replay = idempotency.replay(userId, key, "PATCH", path, request, NotificationContracts.View.class);
        if (replay != null) return replay;
        Notification notification = notifications.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "Notification not found"));
        Instant now = clock.instant();
        if (request.read() != null) notification.setRead(request.read(), now);
        if (request.dismissed() != null) notification.setDismissed(request.dismissed(), now);
        NotificationContracts.View response = view(notifications.save(notification));
        idempotency.save(userId, key, "PATCH", path, request, response, 200);
        return response;
    }
    @Transactional
    public Notification ensureIncident(EnvironmentIncident incident, String title, String body) {
        String dedup = "environment-incident:" + incident.getId();
        return notifications.findByUserIdAndDedupKey(incident.getUserId(), dedup).orElseGet(() -> register(
                new Notification(UuidV7.next(), incident.getUserId(), "ENVIRONMENT_ALERT",
                        "ENVIRONMENT_INCIDENT", incident.getId(), dedup,
                        incident.getSeverity() == null ? "MEDIUM" : incident.getSeverity().name(), title, body,
                        "/environment?zoneId=" + incident.getZoneId(), clock.instant()), NotificationType.ENVIRONMENT_ALERT));
    }
    @Transactional
    public void resolveIncident(EnvironmentIncident incident) {
        notifications.findByUserIdAndDedupKey(incident.getUserId(), "environment-incident:" + incident.getId())
                .ifPresent(value -> value.resolve(clock.instant()));
    }
    @Transactional
    public Notification ensure(UUID userId, NotificationType type, String subjectType, UUID subjectId, String dedup,
                               String priority, String title, String body, String deepLink) {
        return notifications.findByUserIdAndDedupKey(userId,dedup).orElseGet(() -> register(new Notification(UuidV7.next(),userId,type.name(),subjectType,subjectId,dedup,priority,title,body,deepLink,clock.instant()),type));
    }
    private Notification register(Notification notification, NotificationType type) {
        var preference=preferences.findByUserIdAndType(notification.getUserId(),type).orElse(null);
        boolean inApp=preference==null||preference.isInAppEnabled(); boolean email=preference!=null&&preference.isEmailEnabled();
        notification.setInAppVisible(inApp); Notification saved=notifications.save(notification);
        deliveries.save(new NotificationDelivery(UuidV7.next(),saved.getId(),"IN_APP",inApp?"DELIVERED":"SKIPPED",clock.instant()));
        deliveries.save(new NotificationDelivery(UuidV7.next(),saved.getId(),"EMAIL",email?"PENDING":"SKIPPED",clock.instant()));
        return saved;
    }
    private NotificationContracts.View view(Notification value) {
        return new NotificationContracts.View(value.getId(), value.getNotificationType(), value.getSubjectType(),
                value.getSubjectId(), value.getPriority(), value.getTitle(), value.getBody(), value.getDeepLink(),
                value.getResolvedAt(), value.getReadAt(), value.getDismissedAt(), value.getCreatedAt(), value.getUpdatedAt());
    }
}
