package com.xianzhi.fridge.shopping.api;

import com.xianzhi.fridge.shared.security.UserPrincipal;
import com.xianzhi.fridge.shared.web.ApiEnvelope;
import com.xianzhi.fridge.shopping.application.ShoppingService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ShoppingController {
    private final ShoppingService shopping;
    public ShoppingController(ShoppingService shopping) { this.shopping = shopping; }

    @GetMapping("/shopping-lists")
    public ApiEnvelope<List<ShoppingContracts.ListView>> lists(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiEnvelope.ok(shopping.listLists(principal.userId()));
    }

    @PostMapping("/shopping-lists")
    public ApiEnvelope<ShoppingContracts.ListView> createList(@AuthenticationPrincipal UserPrincipal principal,
                                                               @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                               @Valid @RequestBody ShoppingContracts.CreateListRequest request) {
        return ApiEnvelope.ok(shopping.createList(principal.userId(), key, request));
    }

    @PostMapping("/shopping-lists/{id}/items")
    public ApiEnvelope<ShoppingContracts.ItemView> addItem(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
                                                            @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                            @Valid @RequestBody ShoppingContracts.CreateItemRequest request) {
        return ApiEnvelope.ok(shopping.addItem(principal.userId(), id, key, request));
    }

    @PatchMapping("/shopping-items/{id}")
    public ApiEnvelope<ShoppingContracts.ItemView> updateItem(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
                                                               @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                               @Valid @RequestBody ShoppingContracts.UpdateItemRequest request) {
        return ApiEnvelope.ok(shopping.updateItem(principal.userId(), id, key, request));
    }

    @DeleteMapping("/shopping-items/{id}")
    public ResponseEntity<ApiEnvelope<Void>> deleteItem(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
                                                         @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        shopping.deleteItem(principal.userId(), id, key);
        return ResponseEntity.ok(ApiEnvelope.ok(null));
    }

    @PostMapping("/shopping-items/{id}/store")
    public ApiEnvelope<ShoppingContracts.ItemView> store(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
                                                         @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                         @Valid @RequestBody ShoppingContracts.StoreRequest request) {
        return ApiEnvelope.ok(shopping.store(principal.userId(), id, key, request));
    }
}
