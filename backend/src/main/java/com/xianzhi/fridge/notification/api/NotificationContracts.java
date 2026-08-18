package com.xianzhi.fridge.notification.api;

import java.time.Instant;
import java.util.UUID;

public final class NotificationContracts {
    private NotificationContracts() { }
    public record UpdateRequest(Boolean read, Boolean dismissed) { }
    public record View(UUID id, String type, String subjectType, UUID subjectId, String priority,
                       String title, String body, String deepLink, Instant resolvedAt, Instant readAt,
                       Instant dismissedAt, Instant createdAt, Instant updatedAt) { }
}
