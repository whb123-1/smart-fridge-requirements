package com.smartfridge.module.food.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartfridge.common.BusinessException;
import com.smartfridge.common.PageResult;
import com.smartfridge.common.UserContext;
import com.smartfridge.module.food.entity.FoodCategory;
import com.smartfridge.module.food.entity.FoodEstimate;
import com.smartfridge.module.food.entity.FoodItem;
import com.smartfridge.module.food.entity.InventoryLog;
import com.smartfridge.module.food.mapper.FoodCategoryMapper;
import com.smartfridge.module.food.mapper.FoodEstimateMapper;
import com.smartfridge.module.food.mapper.FoodItemMapper;
import com.smartfridge.module.food.mapper.InventoryLogMapper;
import com.smartfridge.module.reminder.service.ReminderService;
import com.smartfridge.module.zone.entity.FridgeZone;
import com.smartfridge.module.zone.mapper.FridgeZoneMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FoodService {

    private final FoodItemMapper foodItemMapper;
    private final FoodCategoryMapper categoryMapper;
    private final FoodEstimateMapper estimateMapper;
    private final InventoryLogMapper logMapper;
    private final FridgeZoneMapper zoneMapper;
    private final ExpiryService expiryService;
    private final ReminderService reminderService;

    public PageResult<FoodVO> list(int page, int size, String keyword, Long categoryId,
                                   Long zoneId, String status, String itemType) {
        LambdaQueryWrapper<FoodItem> qw = new LambdaQueryWrapper<FoodItem>()
                .eq(FoodItem::getUserId, UserContext.get());
        if (StringUtils.hasText(keyword)) {
            qw.like(FoodItem::getName, keyword);
        }
        if (categoryId != null) {
            qw.eq(FoodItem::getCategoryId, categoryId);
        }
        if (zoneId != null) {
            qw.eq(FoodItem::getZoneId, zoneId);
        }
        if (StringUtils.hasText(status)) {
            qw.eq(FoodItem::getStatus, status);
        }
        if (StringUtils.hasText(itemType)) {
            List<Long> ids = categoryMapper.selectList(new LambdaQueryWrapper<FoodCategory>()
                            .eq(FoodCategory::getItemType, itemType))
                    .stream().map(FoodCategory::getId).toList();
            if (ids.isEmpty()) {
                return new PageResult<>(0, Collections.emptyList());
            }
            qw.in(FoodItem::getCategoryId, ids);
        }
        qw.orderByDesc(FoodItem::getStatus).orderByAsc(FoodItem::getSuggestedExpiryDate)
                .orderByDesc(FoodItem::getCreatedAt);
        Page<FoodItem> p = foodItemMapper.selectPage(new Page<>(Math.max(1, page), Math.min(Math.max(1, size), 100)), qw);
        List<FoodVO> list = p.getRecords().stream().map(this::toVO).toList();
        return new PageResult<>(p.getTotal(), list);
    }

    public List<FoodCategory> categories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<FoodCategory>()
                .orderByAsc(FoodCategory::getSort)
                .orderByAsc(FoodCategory::getId));
    }

    public List<FoodEstimate> estimates() {
        return estimateMapper.selectList(null);
    }

    public FoodItem add(FoodReq req) {
        if (!StringUtils.hasText(req.name())) {
            throw new BusinessException("食材名称不能为空");
        }
        if (req.quantity() == null || req.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("数量必须大于 0");
        }
        FoodCategory cat = resolveCategory(req);
        FoodItem item = new FoodItem();
        item.setUserId(UserContext.get());
        item.setName(req.name().trim());
        item.setCategoryId(cat != null ? cat.getId() : req.categoryId());
        item.setZoneId(req.zoneId());
        item.setQuantity(req.quantity());
        item.setUnit(StringUtils.hasText(req.unit()) ? req.unit()
                : (cat != null ? cat.getDefaultUnit() : "个"));
        item.setUnitType(StringUtils.hasText(req.unitType()) ? req.unitType()
                : (cat != null ? cat.getUnitType() : "count"));
        validateUnit(item.getUnit(), item.getUnitType());
        item.setStatus("in_stock");
        item.setEntryDate(req.entryDate() == null ? LocalDate.now() : req.entryDate());
        item.setOpenedDate(req.openedDate());
        item.setPackageExpiryDate(req.packageExpiryDate());
        item.setLowStockThreshold(req.lowStockThreshold());
        item.setNote(req.note());
        foodItemMapper.insert(item);
        afterSave(item, "食材录入");
        return item;
    }

    public FoodItem update(Long id, FoodReq req) {
        FoodItem item = owned(id);
        FoodCategory cat = resolveCategory(req);
        BigDecimal beforeQty = item.getQuantity();
        item.setName(req.name() == null ? item.getName() : req.name().trim());
        item.setCategoryId(cat != null ? cat.getId() : item.getCategoryId());
        item.setZoneId(req.zoneId());
        item.setQuantity(req.quantity() == null ? item.getQuantity() : req.quantity());
        item.setUnit(StringUtils.hasText(req.unit()) ? req.unit() : item.getUnit());
        item.setUnitType(StringUtils.hasText(req.unitType()) ? req.unitType() : item.getUnitType());
        validateUnit(item.getUnit(), item.getUnitType());
        item.setEntryDate(req.entryDate() == null ? item.getEntryDate() : req.entryDate());
        item.setOpenedDate(req.openedDate());
        item.setPackageExpiryDate(req.packageExpiryDate());
        item.setLowStockThreshold(req.lowStockThreshold());
        item.setNote(req.note());
        expiryService.apply(item);
        refreshLowStock(item);
        foodItemMapper.updateById(item);
        // 分区、开封日期、包装保质期、低库存阈值、备注允许清空为 null
        foodItemMapper.update(null, new LambdaUpdateWrapper<FoodItem>()
                .eq(FoodItem::getId, id)
                .set(FoodItem::getZoneId, req.zoneId())
                .set(FoodItem::getOpenedDate, req.openedDate())
                .set(FoodItem::getPackageExpiryDate, req.packageExpiryDate())
                .set(FoodItem::getLowStockThreshold, req.lowStockThreshold())
                .set(FoodItem::getNote, req.note()));
        if (req.quantity() != null && req.quantity().compareTo(beforeQty) != 0) {
            logChange(item, "adjust", req.quantity().subtract(beforeQty), "修改库存数量");
        }
        return item;
    }

    public void delete(Long id) {
        FoodItem item = owned(id);
        logChange(item, "adjust", item.getQuantity().negate(), "删除食材");
        foodItemMapper.deleteById(id);
    }

    public FoodItem consume(Long id, ConsumeReq req) {
        FoodItem item = owned(id);
        if (req.quantity() == null || req.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("消耗数量必须大于 0");
        }
        if (item.getQuantity().compareTo(req.quantity()) < 0) {
            throw new BusinessException("库存不足，当前仅剩 " + item.getQuantity() + " " + item.getUnit());
        }
        BigDecimal before = item.getQuantity();
        BigDecimal after = before.subtract(req.quantity());
        item.setQuantity(after);
        if (after.compareTo(BigDecimal.ZERO) == 0) {
            item.setStatus("consumed");
        }
        refreshLowStock(item);
        foodItemMapper.updateById(item);
        logChange(item, "consume", req.quantity(), req.recipeId(), req.remark());
        return item;
    }

    public FoodItem markExpired(Long id) {
        FoodItem item = owned(id);
        BigDecimal qty = item.getQuantity();
        item.setStatus("expired");
        foodItemMapper.updateById(item);
        logChange(item, "expire", qty, "食材已过期");
        return item;
    }

    public FoodItem discard(Long id, String remark) {
        FoodItem item = owned(id);
        BigDecimal qty = item.getQuantity();
        item.setStatus("discarded");
        item.setQuantity(BigDecimal.ZERO);
        foodItemMapper.updateById(item);
        logChange(item, "discard", qty, StringUtils.hasText(remark) ? remark : "食材丢弃");
        return item;
    }

    private void afterSave(FoodItem item, String remark) {
        expiryService.apply(item);
        refreshLowStock(item);
        foodItemMapper.updateById(item);
        logChange(item, "in", item.getQuantity(), remark);
        reminderService.checkExpiry(item);
    }

    private void refreshLowStock(FoodItem item) {
        boolean low = item.getLowStockThreshold() != null
                && item.getQuantity().compareTo(item.getLowStockThreshold()) <= 0;
        item.setIsLowStock(low ? 1 : 0);
        if (low) {
            reminderService.checkLowStock(item);
        }
    }

    private void logChange(FoodItem item, String type, BigDecimal qty, String remark) {
        logChange(item, type, qty, null, remark);
    }

    private void logChange(FoodItem item, String type, BigDecimal qty, Long recipeId, String remark) {
        InventoryLog log = new InventoryLog();
        log.setUserId(item.getUserId());
        log.setFoodItemId(item.getId());
        log.setFoodName(item.getName());
        log.setChangeType(type);
        log.setChangeQty(qty);
        log.setChangeUnit(item.getUnit());
        log.setBeforeQty(null);
        log.setAfterQty(item.getQuantity());
        log.setRelatedRecipeId(recipeId);
        log.setRemark(remark);
        log.setCreatedAt(LocalDateTime.now());
        logMapper.insert(log);
    }

    private FoodCategory resolveCategory(FoodReq req) {
        if (req.categoryId() != null) {
            return categoryMapper.selectById(req.categoryId());
        }
        if (StringUtils.hasText(req.categoryName())) {
            return categoryMapper.selectOne(new LambdaQueryWrapper<FoodCategory>()
                    .eq(FoodCategory::getName, req.categoryName().trim())
                    .last("LIMIT 1"));
        }
        return null;
    }

    private static final Set<String> WEIGHT_UNITS = Set.of(
            "克", "千克", "斤", "公斤", "块", "片", "条", "袋", "包");
    private static final Set<String> VOLUME_UNITS = Set.of(
            "毫升", "升", "瓶", "盒", "杯", "袋", "包");
    private static final Set<String> COUNT_UNITS = Set.of(
            "个", "根", "只", "块", "包", "盒", "袋", "瓶", "把", "头", "条");

    private void validateUnit(String unit, String unitType) {
        if (unit == null || unitType == null) {
            return;
        }
        boolean ok = switch (unitType) {
            case "weight" -> WEIGHT_UNITS.contains(unit);
            case "volume" -> VOLUME_UNITS.contains(unit);
            case "count" -> COUNT_UNITS.contains(unit);
            default -> true;
        };
        if (!ok) {
            String hint = switch (unitType) {
                case "weight" -> "该食材应按重量计量（克/千克/斤等）";
                case "volume" -> "该食材应按容量计量（毫升/升/瓶等）";
                default -> "该食材应按数量计量（个/根/只等）";
            };
            throw new BusinessException("计量单位「" + unit + "」不适用：" + hint);
        }
    }

    private FoodItem owned(Long id) {
        FoodItem item = foodItemMapper.selectById(id);
        if (item == null || !item.getUserId().equals(UserContext.get())) {
            throw new BusinessException(404, "食材不存在");
        }
        return item;
    }

    private FoodVO toVO(FoodItem item) {
        FoodCategory cat = item.getCategoryId() == null ? null : categoryMapper.selectById(item.getCategoryId());
        FridgeZone zone = item.getZoneId() == null ? null : zoneMapper.selectById(item.getZoneId());
        Long daysToExpiry = item.getSuggestedExpiryDate() == null ? null
                : ChronoUnit.DAYS.between(LocalDate.now(), item.getSuggestedExpiryDate());
        return new FoodVO(item.getId(), item.getName(),
                item.getCategoryId(), cat == null ? null : cat.getName(), cat == null ? null : cat.getItemType(),
                item.getZoneId(), zone == null ? null : zone.getName(),
                item.getQuantity(), item.getUnit(), item.getUnitType(),
                item.getStatus(), statusText(item),
                item.getEntryDate(), item.getOpenedDate(), item.getPackageExpiryDate(),
                item.getSuggestedExpiryDate(), item.getExpiryBasis(), daysToExpiry,
                item.getLowStockThreshold(), item.getIsLowStock(), item.getNote(), item.getCreatedAt());
    }

    private String statusText(FoodItem item) {
        if ("in_stock".equals(item.getStatus())) {
            return "在库";
        }
        if ("consumed".equals(item.getStatus())) {
            return "已食用";
        }
        if ("expired".equals(item.getStatus())) {
            return "已过期";
        }
        if ("discarded".equals(item.getStatus())) {
            return "已丢弃";
        }
        return item.getStatus();
    }

    public record FoodReq(
            String name,
            Long categoryId,
            String categoryName,
            Long zoneId,
            BigDecimal quantity,
            String unit,
            String unitType,
            LocalDate entryDate,
            LocalDate openedDate,
            LocalDate packageExpiryDate,
            BigDecimal lowStockThreshold,
            String note) {
    }

    public record ConsumeReq(
            BigDecimal quantity,
            Long recipeId,
            String remark) {
    }

    public record FoodVO(
            Long id,
            String name,
            Long categoryId,
            String categoryName,
            String itemType,
            Long zoneId,
            String zoneName,
            BigDecimal quantity,
            String unit,
            String unitType,
            String status,
            String statusText,
            LocalDate entryDate,
            LocalDate openedDate,
            LocalDate packageExpiryDate,
            LocalDate suggestedExpiryDate,
            String expiryBasis,
            Long daysToExpiry,
            BigDecimal lowStockThreshold,
            Integer isLowStock,
            String note,
            LocalDateTime createdAt) {
    }
}
