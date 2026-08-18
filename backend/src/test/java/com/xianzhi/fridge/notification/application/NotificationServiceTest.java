package com.xianzhi.fridge.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xianzhi.fridge.fridge.domain.SensorMetric;
import com.xianzhi.fridge.notification.infrastructure.Notification;
import com.xianzhi.fridge.notification.infrastructure.NotificationRepository;
import com.xianzhi.fridge.shared.application.IdempotencyService;
import com.xianzhi.fridge.telemetry.domain.IncidentReason;
import com.xianzhi.fridge.telemetry.domain.IncidentSeverity;
import com.xianzhi.fridge.telemetry.infrastructure.EnvironmentIncident;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class NotificationServiceTest {
    @Test
    void incidentNotificationIsDeduplicatedAndResolvedWithoutBeingMarkedRead() {
        NotificationRepository repository = mock(NotificationRepository.class);
        AtomicReference<Notification> stored = new AtomicReference<>();
        UUID userId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-18T06:00:00Z");
        EnvironmentIncident incident = new EnvironmentIncident(incidentId, userId, UUID.randomUUID(), UUID.randomUUID(),
                SensorMetric.TEMPERATURE, IncidentReason.OUT_OF_RANGE, "HIGH", IncidentSeverity.MODERATE,
                now.minusSeconds(900), now, BigDecimal.valueOf(3), now);

        when(repository.findByUserIdAndDedupKey(userId, "environment-incident:" + incidentId))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(repository.save(org.mockito.ArgumentMatchers.any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            stored.set(notification);
            return notification;
        });

        NotificationService service = new NotificationService(repository, mock(IdempotencyService.class),
                Clock.fixed(now, ZoneOffset.UTC));
        Notification first = service.ensureIncident(incident, "冷藏区温度异常", "温度已连续偏离安全范围");
        Notification replay = service.ensureIncident(incident, "不会重复创建", "不会覆盖首次内容");

        assertThat(replay).isSameAs(first);
        verify(repository, times(1)).save(first);

        service.resolveIncident(incident);
        assertThat(first.getResolvedAt()).isEqualTo(now);
        assertThat(first.getReadAt()).isNull();
    }
}
