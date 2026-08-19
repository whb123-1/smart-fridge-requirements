package com.xianzhi.fridge.identity.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.xianzhi.fridge.identity.domain.UserRole;
import com.xianzhi.fridge.identity.domain.UserStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AdminUserContracts {
    private AdminUserContracts() { }
    public record UserView(UUID id, String username, String email, String displayName, UserRole role, UserStatus status,
                           boolean onboardingCompleted, boolean passwordChangeRequired, long activeSessionCount,
                           Instant lastLoginAt, Instant createdAt, Instant updatedAt, Instant deletedAt,
                           Instant deletionRequestedAt, Instant anonymizedAt) { }
    public record PageView<T>(List<T> items, long total, int page, int size, int totalPages) { }
    public record StatusRequest(@NotNull UserStatus status) { }
    public record RoleRequest(@NotNull UserRole role) { }
    public record ActionView(UUID userId, String action, UserRole role, UserStatus status, Instant occurredAt) { }
    public record TemporaryPasswordView(UUID userId, String temporaryPassword, Instant expiresAt) { }
    public record AuditView(UUID id, UUID actorUserId, UUID targetUserId, String eventType, String traceId,
                            JsonNode metadata, Instant createdAt) { }
}
