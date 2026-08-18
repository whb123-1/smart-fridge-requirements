package com.xianzhi.fridge.shopping.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianzhi.fridge.fridge.infrastructure.Fridge;
import com.xianzhi.fridge.fridge.infrastructure.FridgeRepository;
import com.xianzhi.fridge.inventory.api.InventoryContracts;
import com.xianzhi.fridge.inventory.application.InventoryService;
import com.xianzhi.fridge.inventory.domain.FoodCategory;
import com.xianzhi.fridge.inventory.infrastructure.InventoryItemRepository;
import com.xianzhi.fridge.shared.application.AuditService;
import com.xianzhi.fridge.shared.domain.Hashing;
import com.xianzhi.fridge.shared.domain.UuidV7;
import com.xianzhi.fridge.shared.infrastructure.IdempotencyRecord;
import com.xianzhi.fridge.shared.infrastructure.IdempotencyRecordRepository;
import com.xianzhi.fridge.shared.web.ApiException;
import com.xianzhi.fridge.shopping.api.ShoppingContracts;
import com.xianzhi.fridge.shopping.domain.ShoppingStatus;
import com.xianzhi.fridge.shopping.infrastructure.ShoppingItem;
import com.xianzhi.fridge.shopping.infrastructure.ShoppingItemRepository;
import com.xianzhi.fridge.shopping.infrastructure.ShoppingList;
import com.xianzhi.fridge.shopping.infrastructure.ShoppingListRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShoppingService {
    private final FridgeRepository fridges;
    private final ShoppingListRepository lists;
    private final ShoppingItemRepository items;
    private final InventoryService inventory;
    private final IdempotencyRecordRepository idempotency;
    private final ObjectMapper mapper;
    private final AuditService audit;
    private final Clock clock = Clock.systemUTC();

    public ShoppingService(FridgeRepository fridges, ShoppingListRepository lists, ShoppingItemRepository items,
                           InventoryService inventory, IdempotencyRecordRepository idempotency,
                           ObjectMapper mapper, AuditService audit) {
        this.fridges = fridges; this.lists = lists; this.items = items; this.inventory = inventory;
        this.idempotency = idempotency; this.mapper = mapper; this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ShoppingContracts.ListView> listLists(UUID userId) {
        return lists.findByUserIdOrderByCreatedAtAsc(userId).stream().map(this::toListView).toList();
    }

    @Transactional
    public ShoppingContracts.ListView createList(UUID userId, String key, ShoppingContracts.CreateListRequest request) {
        requireKey(key);
        String path = "/api/v1/shopping-lists";
        String hash = hash("POST", path, request);
        ShoppingContracts.ListView replay = replay(userId, key, hash, ShoppingContracts.ListView.class);
        if (replay != null) return replay;
        Fridge fridge = ownedFridge(userId, request.fridgeId());
        ShoppingList list = lists.save(new ShoppingList(UuidV7.next(), userId, fridge.getId(), request.name().trim()));
        ShoppingContracts.ListView response = toListView(list);
        save(userId, key, hash, "POST", path, response);
        audit.record(userId, "SHOPPING_LIST_CREATED");
        return response;
    }

    @Transactional
    public ShoppingContracts.ItemView addItem(UUID userId, UUID listId, String key, ShoppingContracts.CreateItemRequest request) {
        requireKey(key);
        String path = "/api/v1/shopping-lists/" + listId + "/items";
        String hash = hash("POST", path, request);
        ShoppingContracts.ItemView replay = replay(userId, key, hash, ShoppingContracts.ItemView.class);
        if (replay != null) return replay;
        ShoppingList list = ownedList(userId, listId);
        requireQuantityUnitPair(request.quantity(), request.unit());
        ShoppingItem item = items.save(new ShoppingItem(UuidV7.next(), list.getId(), request.name().trim(),
                normalizeCategory(request.category()), request.quantity(), trimToNull(request.unit()), request.note(), ShoppingStatus.PENDING,
                request.sourceType() == null || request.sourceType().isBlank() ? "MANUAL" : request.sourceType().trim()));
        ShoppingContracts.ItemView response = toItemView(item);
        save(userId, key, hash, "POST", path, response);
        return response;
    }

    @Transactional
    public ShoppingContracts.ItemView updateItem(UUID userId, UUID itemId, String key, ShoppingContracts.UpdateItemRequest request) {
        requireKey(key);
        String path = "/api/v1/shopping-items/" + itemId;
        String hash = hash("PATCH", path, request);
        ShoppingContracts.ItemView replay = replay(userId, key, hash, ShoppingContracts.ItemView.class);
        if (replay != null) return replay;
        ShoppingItem item = ownedItem(userId, itemId);
        String name = request.name() == null || request.name().isBlank() ? item.getName() : request.name().trim();
        String category = request.category() == null || request.category().isBlank() ? item.getCategory() : normalizeCategory(request.category());
        BigDecimal quantity = request.quantity() == null ? item.getQuantity() : request.quantity();
        String unit = request.unit() == null || request.unit().isBlank() ? item.getUnit() : request.unit();
        String note = request.note() == null ? item.getNote() : request.note();
        ShoppingStatus status = request.status() == null ? item.getStatus() : request.status();
        requireQuantityUnitPair(quantity, unit);
        if (item.getStatus() == ShoppingStatus.STORED && status != ShoppingStatus.STORED) {
            throw new ApiException(HttpStatus.CONFLICT, "SHOPPING_ITEM_ALREADY_STORED", "A stored shopping item cannot be reopened");
        }
        if (item.getStatus() != ShoppingStatus.STORED && status == ShoppingStatus.STORED) {
            throw new ApiException(HttpStatus.CONFLICT, "SHOPPING_ITEM_STORE_REQUIRED", "Use the store endpoint to move a shopping item into inventory");
        }
        item.update(name, category, quantity, unit, note, status);
        ShoppingContracts.ItemView response = toItemView(items.save(item));
        save(userId, key, hash, "PATCH", path, response);
        return response;
    }

    @Transactional
    public void deleteItem(UUID userId, UUID itemId, String key) {
        requireKey(key);
        String path = "/api/v1/shopping-items/" + itemId;
        String hash = hash("DELETE", path, itemId);
        if (replay(userId, key, hash, Map.class) != null) return;
        ShoppingItem item = ownedItem(userId, itemId);
        items.delete(item);
        save(userId, key, hash, "DELETE", path, Map.of("deleted", true));
    }

    @Transactional
    public ShoppingContracts.ItemView store(UUID userId, UUID itemId, String key, ShoppingContracts.StoreRequest request) {
        requireKey(key);
        String path = "/api/v1/shopping-items/" + itemId + "/store";
        String hash = hash("POST", path, request);
        ShoppingContracts.ItemView replay = replay(userId, key, hash, ShoppingContracts.ItemView.class);
        if (replay != null) return replay;
        ShoppingItem item = lockedItem(userId, itemId);
        if (item.getStatus() == ShoppingStatus.STORED) {
            ShoppingContracts.ItemView response = toItemView(item);
            save(userId, key, hash, "POST", path, response);
            return response;
        }
        UUID fridgeId = request.fridgeId() == null ? ownedList(userId, item.getListId()).getFridgeId() : request.fridgeId();
        ownedFridge(userId, fridgeId);
        BigDecimal quantity = request.quantity() == null ? item.getQuantity() : request.quantity();
        String unit = request.unit() == null || request.unit().isBlank() ? item.getUnit() : request.unit();
        requireQuantityUnitPair(quantity, unit);
        if (quantity == null || quantity.signum() <= 0) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "A positive quantity is required");
        FoodCategory category = parseCategory(item.getCategory());
        InventoryContracts.BatchRequest batch = new InventoryContracts.BatchRequest(request.zoneId(), request.storedAt(), null, null,
                request.shelfLifeDays(), quantity, unit.trim(), null);
        InventoryContracts.CreateItemRequest create = new InventoryContracts.CreateItemRequest(fridgeId, item.getName(), category, null,
                batch.unit(), List.of(batch));
        inventory.createItem(userId, "shopping-store:" + key, create);
        item.update(item.getName(), item.getCategory(), item.getQuantity(), item.getUnit(), item.getNote(), ShoppingStatus.STORED);
        ShoppingContracts.ItemView response = toItemView(items.save(item));
        save(userId, key, hash, "POST", path, response);
        audit.record(userId, "SHOPPING_ITEM_STORED");
        return response;
    }

    private ShoppingContracts.ListView toListView(ShoppingList list) {
        return new ShoppingContracts.ListView(list.getId(), list.getFridgeId(), list.getName(), items.findByListIdOrderByCreatedAtDesc(list.getId()).stream().map(this::toItemView).toList());
    }
    private ShoppingContracts.ItemView toItemView(ShoppingItem item) {
        return new ShoppingContracts.ItemView(item.getId(), item.getName(), item.getCategory(), item.getQuantity(), item.getUnit(), item.getNote(), item.getStatus(), item.getSourceType());
    }
    private ShoppingList ownedList(UUID userId, UUID listId) {
        return lists.findByIdAndUserId(listId, userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SHOPPING_LIST_NOT_FOUND", "Shopping list not found"));
    }
    private ShoppingItem ownedItem(UUID userId, UUID itemId) {
        ShoppingItem item = items.findById(itemId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SHOPPING_ITEM_NOT_FOUND", "Shopping item not found"));
        ownedList(userId, item.getListId());
        return item;
    }
    private ShoppingItem lockedItem(UUID userId, UUID itemId) {
        ShoppingItem item = items.findById(itemId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SHOPPING_ITEM_NOT_FOUND", "Shopping item not found"));
        ownedList(userId, item.getListId());
        return item;
    }
    private Fridge ownedFridge(UUID userId, UUID fridgeId) {
        return fridges.findById(fridgeId).filter(fridge -> userId.equals(fridge.getUserId()) && fridge.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FRIDGE_NOT_FOUND", "Fridge not found"));
    }
    private static String normalizeCategory(String category) {
        if (category == null || category.isBlank()) return "OTHER";
        try { return FoodCategory.valueOf(category.trim().toUpperCase(Locale.ROOT)).name(); }
        catch (IllegalArgumentException ignored) { return "OTHER"; }
    }
    private static FoodCategory parseCategory(String category) {
        try { return FoodCategory.valueOf(normalizeCategory(category)); }
        catch (IllegalArgumentException ignored) { return FoodCategory.OTHER; }
    }
    private static void requireQuantityUnitPair(BigDecimal quantity, String unit) {
        boolean hasQuantity = quantity != null;
        boolean hasUnit = unit != null && !unit.isBlank();
        if (hasQuantity != hasUnit) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Quantity and unit must be provided together");
        }
    }
    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
    private String hash(String method, String path, Object request) {
        try { return Hashing.sha256(method + " " + path + "\n" + mapper.writeValueAsString(request)); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Could not hash request", exception); }
    }
    private <T> T replay(UUID userId, String key, String hash, Class<T> type) {
        IdempotencyRecord previous = idempotency.findByUserIdAndIdempotencyKey(userId, key).orElse(null);
        if (previous == null) return null;
        if (!previous.getRequestHash().equals(hash)) throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "Idempotency key was used for a different request");
        try { return mapper.readValue(previous.getResponseBody(), type); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Could not read idempotency response", exception); }
    }
    private void save(UUID userId, String key, String hash, String method, String path, Object response) {
        try { idempotency.save(new IdempotencyRecord(UuidV7.next(), userId, key, hash, mapper.writeValueAsString(response), clock.instant().plus(Duration.ofDays(7)), method, path, 200)); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Could not store idempotency response", exception); }
    }
    private static void requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Idempotency-Key is required");
    }
}
