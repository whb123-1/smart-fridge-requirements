package com.xianzhi.fridge.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.xianzhi.fridge.notification.infrastructure.NotificationPreference;
import com.xianzhi.fridge.notification.infrastructure.NotificationType;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationDeliveryServiceTest {
    @Test
    void quietHoursSupportOvernightAndDaytimeWindows() {
        NotificationPreference overnight = preference(LocalTime.of(22, 0), LocalTime.of(7, 0));
        assertThat(NotificationDeliveryService.quietHoursEnd(overnight, Instant.parse("2026-08-18T15:00:00Z")))
                .isEqualTo(Instant.parse("2026-08-18T23:00:00Z"));
        assertThat(NotificationDeliveryService.quietHoursEnd(overnight, Instant.parse("2026-08-18T21:00:00Z")))
                .isEqualTo(Instant.parse("2026-08-18T23:00:00Z"));

        NotificationPreference daytime = preference(LocalTime.of(12, 0), LocalTime.of(14, 0));
        assertThat(NotificationDeliveryService.quietHoursEnd(daytime, Instant.parse("2026-08-18T05:00:00Z")))
                .isEqualTo(Instant.parse("2026-08-18T06:00:00Z"));
        assertThat(NotificationDeliveryService.quietHoursEnd(daytime, Instant.parse("2026-08-18T08:00:00Z"))).isNull();
    }

    private static NotificationPreference preference(LocalTime start, LocalTime end) {
        Instant now = Instant.parse("2026-08-18T00:00:00Z");
        NotificationPreference value = new NotificationPreference(UUID.randomUUID(), UUID.randomUUID(),
                NotificationType.EXPIRY_SOON, "Asia/Shanghai", now);
        value.update(true, true, start, end, "Asia/Shanghai", now);
        return value;
    }
}
