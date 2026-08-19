package com.xianzhi.fridge.inventory.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianzhi.fridge.fridge.infrastructure.Fridge;
import com.xianzhi.fridge.fridge.infrastructure.FridgeRepository;
import com.xianzhi.fridge.fridge.infrastructure.FridgeZone;
import com.xianzhi.fridge.fridge.infrastructure.FridgeZoneRepository;
import com.xianzhi.fridge.inventory.api.InventoryContracts;
import com.xianzhi.fridge.inventory.domain.AssessmentSource;
import com.xianzhi.fridge.inventory.domain.AssessmentStatus;
import com.xianzhi.fridge.inventory.domain.BatchStatus;
import com.xianzhi.fridge.inventory.domain.FoodCategory;
import com.xianzhi.fridge.inventory.domain.TransactionType;
import com.xianzhi.fridge.inventory.infrastructure.FoodCatalog;
import com.xianzhi.fridge.inventory.infrastructure.FoodCatalogRepository;
import com.xianzhi.fridge.inventory.infrastructure.FoodStorageProfile;
import com.xianzhi.fridge.inventory.infrastructure.FoodStorageProfileRepository;
import com.xianzhi.fridge.inventory.infrastructure.FoodWeightEstimate;
import com.xianzhi.fridge.inventory.infrastructure.FoodWeightEstimateRepository;
import com.xianzhi.fridge.inventory.infrastructure.InventoryBatch;
import com.xianzhi.fridge.inventory.infrastructure.InventoryBatchRepository;
import com.xianzhi.fridge.inventory.infrastructure.InventoryItem;
import com.xianzhi.fridge.inventory.infrastructure.InventoryItemRepository;
import com.xianzhi.fridge.inventory.infrastructure.InventoryTransaction;
import com.xianzhi.fridge.inventory.infrastructure.InventoryTransactionRepository;
import com.xianzhi.fridge.inventory.infrastructure.ShelfLifeAssessment;
import com.xianzhi.fridge.inventory.infrastructure.ShelfLifeAssessmentRepository;
import com.xianzhi.fridge.shared.application.AuditService;
import com.xianzhi.fridge.shared.domain.Hashing;
import com.xianzhi.fridge.shared.domain.UuidV7;
import com.xianzhi.fridge.shared.infrastructure.IdempotencyRecord;
import com.xianzhi.fridge.shared.infrastructure.IdempotencyRecordRepository;
import com.xianzhi.fridge.shared.infrastructure.OutboxEvent;
import com.xianzhi.fridge.shared.infrastructure.OutboxEventRepository;
import com.xianzhi.fridge.shared.web.ApiException;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
    private final FridgeRepository fridges;
    private final FridgeZoneRepository zones;
    private final FoodCatalogRepository catalogs;
    private final FoodWeightEstimateRepository weights;
    private final FoodStorageProfileRepository profiles;
    private final InventoryItemRepository items;
    private final InventoryBatchRepository batches;
    private final InventoryTransactionRepository transactions;
    private final ShelfLifeAssessmentRepository assessments;
    private final IdempotencyRecordRepository idempotency;
    private final OutboxEventRepository outbox;
    private final ObjectMapper mapper;
    private final AuditService audit;
    private final Clock clock;
    private final JdbcTemplate jdbc;

    public InventoryService(FridgeRepository fridges, FridgeZoneRepository zones, FoodCatalogRepository catalogs,
                            FoodWeightEstimateRepository weights, FoodStorageProfileRepository profiles,
                            InventoryItemRepository items, InventoryBatchRepository batches,
                            InventoryTransactionRepository transactions, ShelfLifeAssessmentRepository assessments,
                            IdempotencyRecordRepository idempotency, OutboxEventRepository outbox,
                            ObjectMapper mapper, AuditService audit, Clock clock, JdbcTemplate jdbc) {
        this.fridges = fridges; this.zones = zones; this.catalogs = catalogs; this.weights = weights; this.profiles = profiles;
        this.items = items; this.batches = batches; this.transactions = transactions; this.assessments = assessments;
        this.idempotency = idempotency; this.outbox = outbox; this.mapper = mapper; this.audit = audit; this.clock = clock; this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<InventoryContracts.ItemView> listItems(UUID userId, UUID fridgeId, UUID zoneId,
                                                        FoodCategory category, String status, String query) {
        List<InventoryItem> owned = fridgeId == null
                ? items.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId)
                : items.findByUserIdAndFridgeIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId, ownedFridge(userId, fridgeId).getId());
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return owned.stream().filter(item -> category == null || item.getCategory() == category)
                .filter(item -> normalizedQuery.isEmpty() || item.getDisplayName().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .map(item -> toItemView(item, zoneId, status)).toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryContracts.TransactionView> listTransactions(UUID userId, UUID fridgeId, int limit) {
        ownedFridge(userId, fridgeId);
        int safeLimit = Math.max(1, Math.min(100, limit));
        return jdbc.query("""
                select BIN_TO_UUID(t.id) id, BIN_TO_UUID(t.batch_id) batch_id, i.display_name, t.type,
                       t.before_quantity, t.after_quantity, t.quantity_delta, t.unit, t.source_type, t.created_at
                  from inventory_transaction t
                  join inventory_batch b on b.id=t.batch_id
                  join inventory_item i on i.id=b.item_id
                 where t.actor_user_id=UUID_TO_BIN(?) and i.fridge_id=UUID_TO_BIN(?)
                 order by t.created_at desc limit ?
                """, (rs, row) -> new InventoryContracts.TransactionView(
                UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("batch_id")),
                rs.getString("display_name"), TransactionType.valueOf(rs.getString("type")),
                rs.getBigDecimal("before_quantity"), rs.getBigDecimal("after_quantity"),
                rs.getBigDecimal("quantity_delta"), rs.getString("unit"), rs.getString("source_type"),
                rs.getTimestamp("created_at").toInstant()), userId.toString(), fridgeId.toString(), safeLimit);
    }

    @Transactional
    public InventoryContracts.ItemView createItem(UUID userId, String key, InventoryContracts.CreateItemRequest request) {
        requireKey(key);
        String path = "/api/v1/inventory/items";
        String hash = hash("POST", path, request);
        InventoryContracts.ItemView replay = replay(userId, key, hash, InventoryContracts.ItemView.class);
        if (replay != null) return replay;
        Fridge fridge = ownedFridge(userId, request.fridgeId());
        FoodCatalog catalog = findCatalog(request.name());
        FoodCategory category = request.category() != null ? request.category() : catalog == null ? FoodCategory.OTHER : catalog.getCategory();
        String unit = request.defaultUnit().trim();
        InventoryItem item = items.save(new InventoryItem(UuidV7.next(), userId, fridge.getId(), catalog == null ? null : catalog.getId(),
                request.name().trim(), category, request.lowStockQuantity(), unit));
        List<InventoryBatch> created = new ArrayList<>();
        for (InventoryContracts.BatchRequest batchRequest : request.batches()) {
            validateZone(userId, fridge.getId(), batchRequest.zoneId());
            InventoryBatch batch = createBatch(item, batchRequest, catalog, clock.instant());
            created.add(batches.save(batch));
            writeTransaction(userId, batch, TransactionType.IN, BigDecimal.ZERO, batch.getRemainingQuantity(), batch.getRemainingQuantity(),
                    "INVENTORY_CREATE", item.getId(), key);
            recalculate(batch, item, catalog);
        }
        publish("InventoryItem", item.getId(), "InventoryBatchCreated", item.getDisplayName());
        InventoryContracts.ItemView response = toItemView(item, null, null);
        saveIdempotency(userId, key, hash, "POST", path, response);
        audit.record(userId, "INVENTORY_CREATED");
        return response;
    }

    @Transactional
    public InventoryContracts.ItemView updateItem(UUID userId, UUID itemId, String key, InventoryContracts.UpdateItemRequest request) {
        requireKey(key);
        String path = "/api/v1/inventory/items/" + itemId;
        String hash = hash("PATCH", path, request);
        InventoryContracts.ItemView replay = replay(userId, key, hash, InventoryContracts.ItemView.class);
        if (replay != null) return replay;
        InventoryItem item = ownedItem(userId, itemId);
        String name = request.name() == null || request.name().isBlank() ? item.getDisplayName() : request.name().trim();
        FoodCategory category = request.category() == null ? item.getCategory() : request.category();
        BigDecimal threshold = request.lowStockQuantity() == null ? item.getLowStockQuantity() : request.lowStockQuantity();
        String unit = request.defaultUnit() == null || request.defaultUnit().isBlank() ? item.getDefaultUnit() : request.defaultUnit().trim();
        item.update(name, category, threshold, unit);
        InventoryContracts.ItemView response = toItemView(items.save(item), null, null);
        saveIdempotency(userId, key, hash, "PATCH", path, response);
        audit.record(userId, "INVENTORY_UPDATED");
        return response;
    }

    @Transactional
    public void deleteItem(UUID userId, UUID itemId, String key) {
        requireKey(key);
        String path = "/api/v1/inventory/items/" + itemId;
        String hash = hash("DELETE", path, itemId);
        if (replay(userId, key, hash, Map.class) != null) return;
        InventoryItem item = ownedItem(userId, itemId);
        List<InventoryBatch> itemBatches = batches.findByItemIdOrderByStoredAtDesc(itemId);
        if (itemBatches.stream().anyMatch(batch -> batch.getStatus() == BatchStatus.ACTIVE && batch.getRemainingQuantity().signum() > 0)) {
            throw new ApiException(HttpStatus.CONFLICT, "INVENTORY_ITEM_HAS_ACTIVE_BATCHES", "Inventory item still has active batches");
        }
        item.softDelete(clock.instant()); items.save(item);
        saveIdempotency(userId, key, hash, "DELETE", path, Map.of("deleted", true));
        audit.record(userId, "INVENTORY_DELETED");
    }

    @Transactional
    public InventoryContracts.BatchView createBatch(UUID userId, UUID itemId, String key, InventoryContracts.BatchRequest request) {
        requireKey(key);
        String path = "/api/v1/inventory/items/" + itemId + "/batches";
        String hash = hash("POST", path, request);
        InventoryContracts.BatchView replay = replay(userId, key, hash, InventoryContracts.BatchView.class);
        if (replay != null) return replay;
        InventoryItem item = ownedItem(userId, itemId);
        validateZone(userId, item.getFridgeId(), request.zoneId());
        FoodCatalog catalog = item.getCatalogId() == null ? null : catalogs.findById(item.getCatalogId()).orElse(null);
        InventoryBatch batch = batches.save(createBatch(item, request, catalog, clock.instant()));
        writeTransaction(userId, batch, TransactionType.IN, BigDecimal.ZERO, batch.getRemainingQuantity(), batch.getRemainingQuantity(),
                "INVENTORY_CREATE", item.getId(), key);
        recalculate(batch, item, catalog);
        publish("InventoryBatch", batch.getId(), "InventoryBatchCreated", item.getDisplayName());
        InventoryContracts.BatchView response = toBatchView(batch);
        saveIdempotency(userId, key, hash, "POST", path, response);
        return response;
    }

    @Transactional
    public InventoryContracts.BatchView updateBatch(UUID userId, UUID batchId, String key, InventoryContracts.UpdateBatchRequest request) {
        requireKey(key);
        String path = "/api/v1/inventory/batches/" + batchId;
        String hash = hash("PATCH", path, request.fingerprint());
        InventoryContracts.BatchView replay = replay(userId, key, hash, InventoryContracts.BatchView.class);
        if (replay != null) return replay;
        InventoryBatch batch = lockedBatch(batchId);
        InventoryItem item = ownedItem(userId, batch.getItemId());
        if (request.hasZoneId()) validateZone(userId, item.getFridgeId(), request.zoneId());
        batch.updateSchedule(
                request.hasZoneId() ? request.zoneId() : batch.getZoneId(),
                request.hasOpenedAt() ? request.openedAt() : batch.getOpenedAt(),
                request.hasPackageExpiresAt() ? request.packageExpiresAt() : batch.getPackageExpiresAt(),
                request.hasShelfLifeDays() ? request.shelfLifeDays() : batch.getShelfLifeDays(),
                request.hasRemindAt() ? request.remindAt() : batch.getRemindAt());
        batches.save(batch);
        FoodCatalog catalog = item.getCatalogId() == null ? null : catalogs.findById(item.getCatalogId()).orElse(null);
        recalculate(batch, item, catalog);
        InventoryContracts.BatchView response = toBatchView(batch);
        saveIdempotency(userId, key, hash, "PATCH", path, response);
        return response;
    }

    @Transactional
    public InventoryContracts.BatchView transact(UUID userId, UUID batchId, String key, InventoryContracts.TransactionRequest request) {
        requireKey(key);
        String path = "/api/v1/inventory/batches/" + batchId + "/transactions";
        String hash = hash("POST", path, request);
        InventoryContracts.BatchView replay = replay(userId, key, hash, InventoryContracts.BatchView.class);
        if (replay != null) return replay;
        if (request.type() == null || request.type() == TransactionType.IN) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Transaction type is invalid");
        InventoryBatch batch = lockedBatch(batchId);
        InventoryItem item = ownedItem(userId, batch.getItemId());
        if (request.quantity() == null) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Quantity is required");
        if (request.unit() != null && !request.unit().equals(batch.getUnit())) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNIT_NOT_CONVERTIBLE", "Units cannot be converted");
        BigDecimal before = batch.getRemainingQuantity();
        BigDecimal after;
        BigDecimal delta;
        if (request.type() == TransactionType.ADJUST) {
            after = request.quantity(); delta = after.subtract(before); batch.adjustTo(after);
        } else {
            if (request.quantity().signum() <= 0 || request.quantity().compareTo(before) > 0) {
                throw new ApiException(HttpStatus.CONFLICT, "INVENTORY_INSUFFICIENT", "Inventory quantity is insufficient");
            }
            after = before.subtract(request.quantity()); delta = after.subtract(before);
            BatchStatus terminal = request.type() == TransactionType.DISCARD ? BatchStatus.DISCARDED
                    : request.type() == TransactionType.EXPIRED ? BatchStatus.EXPIRED : BatchStatus.DEPLETED;
            batch.consume(request.quantity(), terminal);
        }
        batches.save(batch);
        writeTransaction(userId, batch, request.type(), before, after, delta, request.reason(), null, key);
        recalculate(batch, item, item.getCatalogId() == null ? null : catalogs.findById(item.getCatalogId()).orElse(null));
        publish("InventoryBatch", batch.getId(), "InventoryBatchChanged", request.type().name());
        InventoryContracts.BatchView response = toBatchView(batch);
        saveIdempotency(userId, key, hash, "POST", path, response);
        audit.record(userId, "INVENTORY_" + request.type().name());
        return response;
    }

    @Transactional(readOnly = true)
    public List<InventoryContracts.ExpiryView> expiry(UUID userId, UUID fridgeId, String status, Integer days) {
        List<InventoryContracts.ItemView> itemViews = listItems(userId, fridgeId, null, null, null, null);
        Instant cutoff = clock.instant().plus(Duration.ofDays(days == null ? 3 : Math.max(0, days)));
        List<InventoryContracts.ExpiryView> output = new ArrayList<>();
        for (InventoryContracts.ItemView item : itemViews) {
            for (InventoryContracts.BatchView batch : item.batches()) {
                AssessmentStatus assessmentStatus = batch.assessment() == null ? AssessmentStatus.UNKNOWN : batch.assessment().safetyStatus();
                boolean due = batch.assessment() != null && (batch.assessment().estimatedExpiryAt() == null || !batch.assessment().estimatedExpiryAt().isAfter(cutoff));
                if ((status == null || status.equalsIgnoreCase(assessmentStatus.name())) && due) {
                    output.add(new InventoryContracts.ExpiryView(item.id(), item.name(), batch.id(), batch.zoneId(), batch.remainingQuantity(), batch.unit(), batch.status(), batch.assessment()));
                }
            }
        }
        return output;
    }

    @Transactional(readOnly = true)
    public List<InventoryContracts.AssessmentView> assessments(UUID userId, UUID batchId) {
        InventoryBatch batch = batches.findForRead(batchId).orElseThrow(() -> notFound("BATCH_NOT_FOUND", "Batch not found"));
        items.findByIdAndUserId(batch.getItemId(), userId)
                .orElseThrow(() -> notFound("INVENTORY_ITEM_NOT_FOUND", "Inventory item not found"));
        return this.assessments.findByBatchIdInOrderByCalculatedAtDesc(List.of(batchId)).stream().map(this::toAssessment).toList();
    }

    @Transactional(readOnly = true)
    public boolean isUsableForRecipe(UUID userId, UUID batchId, BigDecimal requestedQuantity, String requestedUnit) {
        InventoryBatch batch = batches.findForRead(batchId)
                .orElseThrow(() -> notFound("BATCH_NOT_FOUND", "Batch not found"));
        items.findByIdAndUserId(batch.getItemId(), userId)
                .orElseThrow(() -> notFound("INVENTORY_ITEM_NOT_FOUND", "Inventory item not found"));
        if (requestedUnit != null && !requestedUnit.equals(batch.getUnit())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNIT_NOT_CONVERTIBLE", "Units cannot be converted");
        }
        if (batch.getStatus() != BatchStatus.ACTIVE || batch.getRemainingQuantity().signum() <= 0) return false;
        if (requestedQuantity != null && requestedQuantity.signum() > 0
                && requestedQuantity.compareTo(batch.getRemainingQuantity()) > 0) return false;
        return assessments.findFirstByBatchIdOrderByCalculatedAtDesc(batchId)
                .map(value -> value.getSafetyStatus() != AssessmentStatus.EXPIRED)
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public List<InventoryContracts.CatalogSuggestion> suggestions(String query, int limit) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return List.of();
        return catalogs.findAllByOrderByCanonicalNameAsc().stream()
                .filter(catalog -> catalog.getCanonicalName().toLowerCase(Locale.ROOT).contains(normalized)
                        || (catalog.getAliases() != null && catalog.getAliases().toLowerCase(Locale.ROOT).contains(normalized)))
                .limit(Math.max(1, Math.min(limit, 20)))
                .map(catalog -> new InventoryContracts.CatalogSuggestion(catalog.getId(), catalog.getCanonicalName(), catalog.getCategory(), catalog.getDefaultUnit()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryContracts.WeightEstimate> weightEstimates(UUID catalogId) {
        return weights.findByCatalogIdOrderByLabelAsc(catalogId).stream().map(weight -> new InventoryContracts.WeightEstimate(
                weight.getId(), weight.getCatalogId(), weight.getLabel(), weight.getReferenceGrams(), weight.getUnit(), weight.getSource())).toList();
    }

    private InventoryBatch createBatch(InventoryItem item, InventoryContracts.BatchRequest request, FoodCatalog catalog, Instant now) {
        Instant storedAt = request.storedAt() == null ? now : request.storedAt();
        if (request.quantity().signum() < 0) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Quantity cannot be negative");
        return new InventoryBatch(UuidV7.next(), item.getId(), request.zoneId(), storedAt, request.openedAt(), request.packageExpiresAt(),
                request.shelfLifeDays(), request.quantity(), request.unit().trim(), request.remindAt());
    }

    private void recalculate(InventoryBatch batch, InventoryItem item, FoodCatalog catalog) {
        Instant now = clock.instant();
        Instant base = null;
        AssessmentSource source;
        Integer profileVersion = null;
        String explanation;
        if (batch.getPackageExpiresAt() != null) { base = batch.getPackageExpiresAt(); source = AssessmentSource.PACKAGE_EXPIRY; explanation = "使用用户录入的包装到期时间"; }
        else if (batch.getShelfLifeDays() != null) { base = batch.getStoredAt().plus(Duration.ofDays(batch.getShelfLifeDays())); source = AssessmentSource.USER_SHELF_LIFE; explanation = "使用用户录入的参考保质期"; }
        else if (catalog != null) {
            FoodStorageProfile profile = chooseProfile(item.getCategory(), batch.getZoneId());
            if (profile != null) {
                Integer hours = batch.getOpenedAt() != null ? profile.getOpenedHours() : profile.getUnopenedHours();
                if (hours != null) base = (batch.getOpenedAt() == null ? batch.getStoredAt() : batch.getOpenedAt()).plus(Duration.ofHours(hours));
                profileVersion = profile.getProfileVersion();
            }
            if (base == null && catalog.getDefaultShelfLifeDays() != null) {
                base = batch.getStoredAt().plus(Duration.ofDays(catalog.getDefaultShelfLifeDays()));
            }
            source = batch.getZoneId() == null ? AssessmentSource.REFERENCE_DEFAULT : AssessmentSource.REFERENCE_TARGET;
            explanation = base == null ? "没有足够的日期或储存档案依据" : "按分区目标环境参考估算，未接入实测传感器";
        } else {
            source = batch.getZoneId() == null ? AssessmentSource.REFERENCE_DEFAULT : AssessmentSource.REFERENCE_TARGET;
            explanation = "自定义食材没有目录或日期依据";
        }
        AssessmentStatus status = base == null ? AssessmentStatus.UNKNOWN : base.isBefore(now) ? AssessmentStatus.EXPIRED
                : !base.isAfter(now.plus(Duration.ofDays(3))) ? AssessmentStatus.EXPIRING_SOON : AssessmentStatus.ADVISORY_ONLY;
        ShelfLifeAssessment assessment = new ShelfLifeAssessment(UuidV7.next(), batch.getId(), profileVersion, base, base, source,
                base == null ? "LOW" : source == AssessmentSource.PACKAGE_EXPIRY ? "HIGH" : "MEDIUM", status, explanation);
        assessments.save(assessment);
    }

    private FoodStorageProfile chooseProfile(FoodCategory category, UUID zoneId) {
        String resolvedZoneKind = zoneId == null ? null : zones.findById(zoneId).map(zone -> zone.getKind().name()).orElse(null);
        List<FoodStorageProfile> candidates = profiles.findByCategoryOrderByProfileVersionDesc(category);
        return candidates.stream().filter(profile -> resolvedZoneKind != null && resolvedZoneKind.equals(profile.getZoneKind())).findFirst()
                .orElseGet(() -> candidates.stream().filter(profile -> profile.getZoneKind() == null).findFirst().orElse(null));
    }

    private InventoryContracts.ItemView toItemView(InventoryItem item, UUID zoneId, String status) {
        List<InventoryContracts.BatchView> itemBatches = batches.findByItemIdOrderByStoredAtDesc(item.getId()).stream()
                .filter(batch -> zoneId == null || zoneId.equals(batch.getZoneId()))
                .filter(batch -> status == null || batch.getStatus().name().equalsIgnoreCase(status))
                .map(this::toBatchView).toList();
        BigDecimal total = itemBatches.stream().filter(batch -> batch.status() == BatchStatus.ACTIVE && item.getDefaultUnit().equals(batch.unit()))
                .map(InventoryContracts.BatchView::remainingQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean low = item.getLowStockQuantity() != null && total.compareTo(item.getLowStockQuantity()) <= 0;
        return new InventoryContracts.ItemView(item.getId(), item.getFridgeId(), item.getCatalogId(), item.getDisplayName(), item.getCategory(),
                item.getLowStockQuantity(), item.getDefaultUnit(), low, itemBatches);
    }

    private InventoryContracts.BatchView toBatchView(InventoryBatch batch) {
        return new InventoryContracts.BatchView(batch.getId(), batch.getZoneId(), batch.getStoredAt(), batch.getOpenedAt(), batch.getPackageExpiresAt(),
                batch.getShelfLifeDays(), batch.getInitialQuantity(), batch.getRemainingQuantity(), batch.getUnit(), batch.getStatus(), batch.getRemindAt(),
                assessments.findFirstByBatchIdOrderByCalculatedAtDesc(batch.getId()).map(this::toAssessment).orElse(null));
    }

    private InventoryContracts.AssessmentView toAssessment(ShelfLifeAssessment assessment) {
        return new InventoryContracts.AssessmentView(assessment.getId(), assessment.getEstimatedExpiryAt(), assessment.getBaseExpiryAt(),
                assessment.getCumulativeRiskMinutes(), assessment.getEstimationSource(), assessment.getConfidence(), assessment.getSafetyStatus(),
                assessment.getExplanation(), assessment.getEnvironmentImpacts(), assessment.getCalculatedAt());
    }

    private InventoryItem ownedItem(UUID userId, UUID itemId) {
        return items.findByIdAndUserIdAndDeletedAtIsNull(itemId, userId).orElseThrow(() -> notFound("INVENTORY_ITEM_NOT_FOUND", "Inventory item not found"));
    }

    private InventoryBatch lockedBatch(UUID batchId) {
        return batches.findById(batchId).orElseThrow(() -> notFound("BATCH_NOT_FOUND", "Batch not found"));
    }

    private Fridge ownedFridge(UUID userId, UUID fridgeId) {
        return fridges.findById(fridgeId).filter(fridge -> userId.equals(fridge.getUserId()) && fridge.getDeletedAt() == null)
                .orElseThrow(() -> notFound("FRIDGE_NOT_FOUND", "Fridge not found"));
    }

    private void validateZone(UUID userId, UUID fridgeId, UUID zoneId) {
        if (zoneId == null) return;
        FridgeZone zone = zones.findById(zoneId).orElseThrow(() -> notFound("ZONE_NOT_FOUND", "Zone not found"));
        ownedFridge(userId, fridgeId);
        if (!fridgeId.equals(zone.getFridgeId()) || zone.getDeletedAt() != null) throw notFound("ZONE_NOT_FOUND", "Zone not found");
    }

    private FoodCatalog findCatalog(String name) {
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        return catalogs.findAllByOrderByCanonicalNameAsc().stream().filter(catalog -> catalog.getCanonicalName().toLowerCase(Locale.ROOT).equals(normalized)
                || (catalog.getAliases() != null && List.of(catalog.getAliases().toLowerCase(Locale.ROOT).split(",")).stream().map(String::trim).anyMatch(normalized::equals))).findFirst().orElse(null);
    }

    private void writeTransaction(UUID userId, InventoryBatch batch, TransactionType type, BigDecimal before, BigDecimal after,
                                  BigDecimal delta, String sourceType, UUID sourceId, String key) {
        transactions.save(new InventoryTransaction(UuidV7.next(), batch.getId(), type, before, after, delta, batch.getUnit(), sourceType, sourceId, userId, key));
    }

    private void publish(String aggregateType, UUID aggregateId, String eventType, String message) {
        try { outbox.save(new OutboxEvent(UuidV7.next(), aggregateType, aggregateId, eventType, mapper.writeValueAsString(Map.of("message", message)))); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Could not serialize outbox event", exception); }
    }

    private String hash(String method, String path, Object request) {
        try { return Hashing.sha256(method + " " + path + "\n" + mapper.writeValueAsString(request)); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Could not hash request", exception); }
    }

    private <T> T replay(UUID userId, String key, String hash, Class<T> type) {
        IdempotencyRecord previous = idempotency.findByUserIdAndIdempotencyKey(userId, key).orElse(null);
        if (previous == null) return null;
        if (!previous.getRequestHash().equals(hash)) {
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "Idempotency key was used for a different request");
        }
        try { return mapper.readValue(previous.getResponseBody(), type); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Could not read idempotency response", exception); }
    }

    private void saveIdempotency(UUID userId, String key, String hash, String method, String path, Object response) {
        try { idempotency.save(new IdempotencyRecord(UuidV7.next(), userId, key, hash, mapper.writeValueAsString(response), clock.instant().plus(Duration.ofDays(7)), method, path, 200)); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Could not store idempotency response", exception); }
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Idempotency-Key is required");
    }

    private static ApiException notFound(String code, String message) { return new ApiException(HttpStatus.NOT_FOUND, code, message); }
}
