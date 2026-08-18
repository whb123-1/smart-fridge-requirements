package com.xianzhi.fridge.speech.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.xianzhi.fridge.inventory.api.InventoryContracts;
import com.xianzhi.fridge.speech.domain.VoiceStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public final class VoiceDraftContracts {
    private VoiceDraftContracts() { }
    public record ConfirmRequest(@NotNull @Valid InventoryContracts.CreateItemRequest inventory) { }
    public record View(UUID id, UUID fridgeId, VoiceStatus status, String transcript, JsonNode draft,
                       String failureCode, String failureReason, Instant expiresAt, Instant confirmedAt,
                       Instant createdAt, Instant updatedAt) { }
}
