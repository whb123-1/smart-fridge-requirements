package com.smartfridge.module.stats.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartfridge.common.UserContext;
import com.smartfridge.module.food.entity.FoodItem;
import com.smartfridge.module.food.entity.InventoryLog;
import com.smartfridge.module.food.mapper.FoodItemMapper;
import com.smartfridge.module.food.mapper.InventoryLogMapper;
import com.smartfridge.module.reminder.entity.Reminder;
import com.smartfridge.module.reminder.mapper.ReminderMapper;
import com.smartfridge.module.zone.entity.FridgeZone;
import com.smartfridge.module.zone.mapper.FridgeZoneMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final InventoryLogMapper logMapper;
    private final FoodItemMapper foodItemMapper;
    private final FridgeZoneMapper zoneMapper;
    private final ReminderMapper reminderMapper;

    public ConsumptionStat consumption(String period) {
        boolean weekly = "week".equals(period);
        LocalDateTime start = weekly
                ? LocalDateTime.now().minusWeeks(1)
                : LocalDateTime.now().minusMonths(1);
        List<InventoryLog> logs = logMapper.selectList(new LambdaQueryWrapper<InventoryLog>()
                .eq(InventoryLog::getUserId, UserContext.get())
                .in(InventoryLog::getChangeType, "consume", "expire", "discard")
                .ge(InventoryLog::getCreatedAt, start));

        Map<String, FoodStat> map = new HashMap<>();
        BigDecimal totalConsume = BigDecimal.ZERO;
        BigDecimal totalWasteQty = BigDecimal.ZERO;
        int wasteCount = 0;
        for (InventoryLog log : logs) {
            FoodStat stat = map.computeIfAbsent(log.getFoodName(),
                    n -> new FoodStat(n, BigDecimal.ZERO, 0, BigDecimal.ZERO, 0));
            if ("consume".equals(log.getChangeType())) {
                totalConsume = totalConsume.add(nz(log.getChangeQty()));
                stat.consumedQty = stat.consumedQty.add(nz(log.getChangeQty()));
                stat.consumeCount++;
            } else {
                totalWasteQty = totalWasteQty.add(nz(log.getChangeQty()));
                wasteCount++;
                stat.wasteQty = stat.wasteQty.add(nz(log.getChangeQty()));
                stat.wasteCount++;
            }
        }
        List<FoodStat> foods = new ArrayList<>(map.values());
        foods.sort(Comparator.comparing((FoodStat s) -> s.consumedQty.add(s.wasteQty)).reversed());
        return new ConsumptionStat(weekly ? "周" : "月", totalConsume, totalWasteQty, wasteCount,
                foods.subList(0, Math.min(10, foods.size())));
    }

    public SummaryVO summary() {
        Long userId = UserContext.get();
        long inStock = foodItemMapper.selectCount(new LambdaQueryWrapper<FoodItem>()
                .eq(FoodItem::getUserId, userId).eq(FoodItem::getStatus, "in_stock"));
        long lowStock = foodItemMapper.selectCount(new LambdaQueryWrapper<FoodItem>()
                .eq(FoodItem::getUserId, userId).eq(FoodItem::getIsLowStock, 1));
        long expiredThisMonth = foodItemMapper.selectCount(new LambdaQueryWrapper<FoodItem>()
                .eq(FoodItem::getUserId, userId).eq(FoodItem::getStatus, "expired"));
        long abnormalZones = zoneMapper.selectCount(new LambdaQueryWrapper<FridgeZone>()
                .eq(FridgeZone::getUserId, userId)
                .in(FridgeZone::getStatus, "abnormal", "stale"));
        long unreadReminders = reminderMapper.selectCount(new LambdaQueryWrapper<Reminder>()
                .eq(Reminder::getUserId, userId).eq(Reminder::getIsRead, 0)
                .eq(Reminder::getStatus, "active"));
        return new SummaryVO(inStock, lowStock, expiredThisMonth, abnormalZones, unreadReminders,
                LocalDate.now());
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    public static class FoodStat {
        public final String name;
        public BigDecimal consumedQty;
        public int consumeCount;
        public BigDecimal wasteQty;
        public int wasteCount;

        FoodStat(String name, BigDecimal consumedQty, int consumeCount, BigDecimal wasteQty, int wasteCount) {
            this.name = name;
            this.consumedQty = consumedQty;
            this.consumeCount = consumeCount;
            this.wasteQty = wasteQty;
            this.wasteCount = wasteCount;
        }
    }

    public record ConsumptionStat(String period, BigDecimal totalConsume, BigDecimal totalWasteQty,
                                  int wasteCount, List<FoodStat> foods) {
    }

    public record SummaryVO(long inStock, long lowStock, long expiredThisMonth,
                            long abnormalZones, long unreadReminders, LocalDate date) {
    }
}
