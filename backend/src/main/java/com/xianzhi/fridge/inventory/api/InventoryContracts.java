package com.xianzhi.fridge.inventory.api;

import com.xianzhi.fridge.inventory.domain.AssessmentSource;
import com.xianzhi.fridge.inventory.domain.AssessmentStatus;
import com.xianzhi.fridge.inventory.domain.BatchStatus;
import com.xianzhi.fridge.inventory.domain.FoodCategory;
import com.xianzhi.fridge.inventory.domain.TransactionType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class InventoryContracts {
    private InventoryContracts() { }

    public record CreateItemRequest(
            @NotNull UUID fridgeId,
            @NotBlank @Size(max = 120) String name,
            FoodCategory category,
            @DecimalMin("0") BigDecimal lowStockQuantity,
            @NotBlank @Size(max = 24) String defaultUnit,
            @NotEmpty List<@Valid BatchRequest> batches) { }

    public record BatchRequest(
            UUID zoneId,
            Instant storedAt,
            Instant openedAt,
            @NotNull @DecimalMin("0") BigDecimal quantity,
            @NotBlank @Size(max = 24) String unit,
            Instant remindAt) { }

    public record UpdateItemRequest(
            @Size(max = 120) String name,
            FoodCategory category,
            @DecimalMin("0") BigDecimal lowStockQuantity,
            @Size(max = 24) String defaultUnit) { }

    public static final class UpdateBatchRequest {
        private UUID zoneId;
        private Instant openedAt;
        private Instant remindAt;
        private boolean zoneIdPresent;
        private boolean openedAtPresent;
        private boolean remindAtPresent;

        public UUID zoneId() { return zoneId; }
        public Instant openedAt() { return openedAt; }
        public Instant remindAt() { return remindAt; }

        @JsonSetter("zoneId") public void setZoneId(UUID value) { zoneId = value; zoneIdPresent = true; }
        @JsonSetter("openedAt") public void setOpenedAt(Instant value) { openedAt = value; openedAtPresent = true; }
        @JsonSetter("remindAt") public void setRemindAt(Instant value) { remindAt = value; remindAtPresent = true; }

        @JsonIgnore public boolean hasZoneId() { return zoneIdPresent; }
        @JsonIgnore public boolean hasOpenedAt() { return openedAtPresent; }
        @JsonIgnore public boolean hasRemindAt() { return remindAtPresent; }

        @JsonIgnore
        public Map<String, Object> fingerprint() {
            Map<String, Object> values = new LinkedHashMap<>();
            if (zoneIdPresent) values.put("zoneId", zoneId);
            if (openedAtPresent) values.put("openedAt", openedAt);
            if (remindAtPresent) values.put("remindAt", remindAt);
            return values;
        }
    }

    public record TransactionRequest(
            @NotNull TransactionType type,
            @DecimalMin("0") BigDecimal quantity,
            @Size(max = 24) String unit,
            @Size(max = 255) String reason) { }

    public record AssessmentView(
            UUID id, Instant estimatedExpiryAt, Instant baseExpiryAt, BigDecimal cumulativeRiskMinutes,
            AssessmentSource estimationSource, String confidence, AssessmentStatus safetyStatus,
            String explanation, String environmentImpacts, Instant calculatedAt) { }

    public record BatchView(
            UUID id, UUID zoneId, Instant storedAt, Instant openedAt,
            BigDecimal initialQuantity, BigDecimal remainingQuantity, String unit,
            BatchStatus status, Instant remindAt, AssessmentView assessment) { }

    public record ItemView(
            UUID id, UUID fridgeId, UUID catalogId, String name, FoodCategory category,
            BigDecimal lowStockQuantity, String defaultUnit, boolean lowStock, List<BatchView> batches) { }

    public record ExpiryView(UUID itemId, String itemName, UUID batchId, UUID zoneId,
                             BigDecimal remainingQuantity, String unit, BatchStatus batchStatus,
                             AssessmentView assessment) { }

    public record CatalogSuggestion(UUID id, String name, FoodCategory category, String defaultUnit) { }
    public record WeightEstimate(UUID id, UUID catalogId, String label, BigDecimal referenceGrams, String unit, String source) { }
    public record TransactionView(UUID id, UUID batchId, String itemName, TransactionType type,
                                  BigDecimal beforeQuantity, BigDecimal afterQuantity, BigDecimal quantityDelta,
                                  String unit, String sourceType, Instant createdAt) { }
}
