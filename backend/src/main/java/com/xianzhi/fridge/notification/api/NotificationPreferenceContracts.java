package com.xianzhi.fridge.notification.api;

import com.xianzhi.fridge.notification.infrastructure.NotificationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalTime;
import java.util.List;

public final class NotificationPreferenceContracts {
    private NotificationPreferenceContracts() { }
    public record Entry(@NotNull NotificationType type,@NotNull Boolean inAppEnabled,@NotNull Boolean emailEnabled,
                        LocalTime quietStart,LocalTime quietEnd,@Pattern(regexp="[A-Za-z_]+/[A-Za-z_]+") String timezone) { }
    public record UpdateRequest(@NotEmpty List<@Valid Entry> preferences) { }
    public record View(NotificationType type,boolean inAppEnabled,boolean emailEnabled,LocalTime quietStart,LocalTime quietEnd,String timezone) { }
}
