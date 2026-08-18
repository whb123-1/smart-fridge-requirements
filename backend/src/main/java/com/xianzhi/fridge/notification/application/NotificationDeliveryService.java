package com.xianzhi.fridge.notification.application;

import com.xianzhi.fridge.identity.infrastructure.AppUserRepository;
import com.xianzhi.fridge.notification.infrastructure.*;
import java.time.Clock;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationDeliveryService {
    private final NotificationDeliveryRepository deliveries;private final NotificationRepository notifications;private final NotificationPreferenceRepository preferences;private final AppUserRepository users;private final EmailDeliveryPort email;private final Clock clock;
    public NotificationDeliveryService(NotificationDeliveryRepository deliveries,NotificationRepository notifications,NotificationPreferenceRepository preferences,AppUserRepository users,EmailDeliveryPort email,Clock clock){this.deliveries=deliveries;this.notifications=notifications;this.preferences=preferences;this.users=users;this.email=email;this.clock=clock;}
    @Transactional public void process(){for(NotificationDelivery delivery:deliveries.findTop100ByStatusAndAvailableAtBeforeOrderByAvailableAtAsc("PENDING",clock.instant())){if(!"EMAIL".equals(delivery.getChannel())){delivery.delivered(clock.instant());continue;}if(!email.enabled()){delivery.skipped("EMAIL_DELIVERY_DISABLED",clock.instant());continue;}try{Notification notification=notifications.findById(delivery.getNotificationId()).orElseThrow();NotificationType type=NotificationType.valueOf(notification.getNotificationType());NotificationPreference preference=preferences.findByUserIdAndType(notification.getUserId(),type).orElse(null);Instant resume=quietHoursEnd(preference,clock.instant());if(resume!=null){delivery.postpone(resume,clock.instant());continue;}String recipient=users.findById(notification.getUserId()).orElseThrow().getEmail();email.send(recipient,notification.getTitle(),notification.getBody());delivery.delivered(clock.instant());}catch(RuntimeException exception){delivery.failed(exception,clock.instant());}}}
    static Instant quietHoursEnd(NotificationPreference preference,Instant now){if(preference==null||preference.getQuietStart()==null||preference.getQuietEnd()==null)return null;ZoneId zone=ZoneId.of(preference.getTimezone());ZonedDateTime local=now.atZone(zone);LocalTime time=local.toLocalTime(),start=preference.getQuietStart(),end=preference.getQuietEnd();boolean quiet=start.equals(end)||start.isBefore(end)?!time.isBefore(start)&&time.isBefore(end):!time.isBefore(start)||time.isBefore(end);if(!quiet)return null;LocalDate endDate=local.toLocalDate();if(start.equals(end)||!start.isBefore(end)){if(!time.isBefore(start))endDate=endDate.plusDays(1);}return ZonedDateTime.of(endDate,end,zone).toInstant();}
}
