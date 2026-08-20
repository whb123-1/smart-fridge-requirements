package com.xianzhi.fridge.inventory.api;

import com.xianzhi.fridge.inventory.application.InventoryService;
import com.xianzhi.fridge.inventory.domain.FoodCategory;
import com.xianzhi.fridge.shared.security.UserPrincipal;
import com.xianzhi.fridge.shared.web.ApiEnvelope;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class InventoryController {
    private final InventoryService inventory;
    public InventoryController(InventoryService inventory) { this.inventory = inventory; }

    @GetMapping("/inventory/items")
    public ApiEnvelope<List<InventoryContracts.ItemView>> items(@AuthenticationPrincipal UserPrincipal principal,
                                                                  @RequestParam(required = false) UUID fridgeId,
                                                                  @RequestParam(required = false) UUID zoneId,
                                                                  @RequestParam(required = false) FoodCategory category,
                                                                  @RequestParam(required = false) String status,
                                                                  @RequestParam(required = false) String query) {
        return ApiEnvelope.ok(inventory.listItems(principal.userId(), fridgeId, zoneId, category, status, query));
    }

    @GetMapping("/inventory/transactions")
    public ApiEnvelope<List<InventoryContracts.TransactionView>> transactions(@AuthenticationPrincipal UserPrincipal principal,
                                                                                @RequestParam UUID fridgeId,
                                                                                @RequestParam(defaultValue = "20") int limit) {
        return ApiEnvelope.ok(inventory.listTransactions(principal.userId(), fridgeId, limit));
    }

    @DeleteMapping("/inventory/transactions/{id}")
    public ResponseEntity<ApiEnvelope<Void>> deleteTransaction(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
                                                                 @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        inventory.deleteTransaction(principal.userId(), id, key);
        return ResponseEntity.ok(ApiEnvelope.ok(null));
    }

    @PostMapping("/inventory/items")
    public ApiEnvelope<InventoryContracts.ItemView> createItem(@AuthenticationPrincipal UserPrincipal principal,
                                                                @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                                @Valid @RequestBody InventoryContracts.CreateItemRequest request) {
        return ApiEnvelope.ok(inventory.createItem(principal.userId(), key, request));
    }

    @PatchMapping("/inventory/items/{id}")
    public ApiEnvelope<InventoryContracts.ItemView> updateItem(@AuthenticationPrincipal UserPrincipal principal,
                                                                @PathVariable UUID id,
                                                                @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                                @Valid @RequestBody InventoryContracts.UpdateItemRequest request) {
        return ApiEnvelope.ok(inventory.updateItem(principal.userId(), id, key, request));
    }

    @DeleteMapping("/inventory/items/{id}")
    public ResponseEntity<ApiEnvelope<Void>> deleteItem(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
                                                         @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        inventory.deleteItem(principal.userId(), id, key);
        return ResponseEntity.ok(ApiEnvelope.ok(null));
    }

    @PostMapping("/inventory/items/{id}/batches")
    public ApiEnvelope<InventoryContracts.BatchView> createBatch(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
                                                                  @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                                  @Valid @RequestBody InventoryContracts.BatchRequest request) {
        return ApiEnvelope.ok(inventory.createBatch(principal.userId(), id, key, request));
    }

    @PatchMapping("/inventory/batches/{id}")
    public ApiEnvelope<InventoryContracts.BatchView> updateBatch(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
                                                                  @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                                  @Valid @RequestBody InventoryContracts.UpdateBatchRequest request) {
        return ApiEnvelope.ok(inventory.updateBatch(principal.userId(), id, key, request));
    }

    @PostMapping("/inventory/batches/{id}/transactions")
    public ApiEnvelope<InventoryContracts.BatchView> transact(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
                                                               @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                               @Valid @RequestBody InventoryContracts.TransactionRequest request) {
        return ApiEnvelope.ok(inventory.transact(principal.userId(), id, key, request));
    }

    @GetMapping("/expiry")
    public ApiEnvelope<List<InventoryContracts.ExpiryView>> expiry(@AuthenticationPrincipal UserPrincipal principal,
                                                                    @RequestParam(required = false) UUID fridgeId,
                                                                    @RequestParam(required = false) String status,
                                                                    @RequestParam(required = false) Integer days) {
        return ApiEnvelope.ok(inventory.expiry(principal.userId(), fridgeId, status, days));
    }

    @GetMapping("/inventory/batches/{id}/assessments")
    public ApiEnvelope<List<InventoryContracts.AssessmentView>> assessments(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ApiEnvelope.ok(inventory.assessments(principal.userId(), id));
    }

    @GetMapping("/catalog/suggestions")
    public ApiEnvelope<List<InventoryContracts.CatalogSuggestion>> suggestions(@RequestParam(required = false) String query,
                                                                                 @RequestParam(defaultValue = "6") int limit) {
        return ApiEnvelope.ok(inventory.suggestions(query, limit));
    }

    @GetMapping("/catalog/weight-estimates")
    public ApiEnvelope<List<InventoryContracts.WeightEstimate>> weightEstimates(@RequestParam UUID catalogId) {
        return ApiEnvelope.ok(inventory.weightEstimates(catalogId));
    }
}
