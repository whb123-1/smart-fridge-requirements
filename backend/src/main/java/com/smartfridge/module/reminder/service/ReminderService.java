package com.smartfridge.module.reminder.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartfridge.common.BusinessException;
import com.smartfridge.common.UserContext;
import com.smartfridge.module.food.entity.FoodItem;
import com.smartfridge.module.reminder.entity.Reminder;
import com.smartfridge.module.reminder.mapper.ReminderMapper;
import com.smartfridge.module.zone.entity.FridgeZone;
import com.smartfridge.module.zone.entity.ZoneRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReminderService {

    private final ReminderMapper reminderMapper;

    public List<Reminder> list(String status) {
        LambdaQueryWrapper<Reminder> qw = new LambdaQueryWrapper<Reminder>()
                .eq(Reminder::getUserId, UserContext.get());
        if (status != null && !status.isBlank()) {
            qw.eq(Reminder::getStatus, status);
        }
        qw.orderByDesc(Reminder::getCreatedAt);
        return reminderMapper.selectList(qw);
    }

    public long unreadCount() {
        return reminderMapper.selectCount(new LambdaQueryWrapper<Reminder>()
                .eq(Reminder::getUserId, UserContext.get())
                .eq(Reminder::getIsRead, 0)
                .eq(Reminder::getStatus, "active"));
    }

    public void markRead(Long id) {
        Reminder r = owned(id);
        r.setIsRead(1);
        reminderMapper.updateById(r);
    }

    public void dismiss(Long id) {
        Reminder r = owned(id);
        r.setStatus("dismissed");
        reminderMapper.updateById(r);
    }

    public void checkExpiry(FoodItem food) {
        if (food.getSuggestedExpiryDate() == null) {
            return;
        }
        long days = ChronoUnit.DAYS.between(LocalDate.now(), food.getSuggestedExpiryDate());
        String title;
        String content;
        if (days < 0) {
            title = "食材已过期";
            content = String.format("「%s」已于 %s 过期，建议尽快检查并处理。",
                    food.getName(), food.getSuggestedExpiryDate());
        } else if (days <= 3) {
            title = "食材即将过期";
            content = String.format("「%s」建议在 %s 前食用，请留意（%d 天）。",
                    food.getName(), food.getSuggestedExpiryDate(), days);
        } else {
            return;
        }
        if (hasActive(food.getUserId(), food.getId(), null, "expiry")) {
            return;
        }
        Reminder r = new Reminder();
        r.setUserId(food.getUserId());
        r.setFoodItemId(food.getId());
        r.setType("expiry");
        r.setTitle(title);
        r.setContent(content);
        r.setRemindTime(LocalDateTime.now());
        r.setIsRead(0);
        r.setStatus("active");
        reminderMapper.insert(r);
    }

    public void checkLowStock(FoodItem food) {
        if (food.getIsLowStock() == null || food.getIsLowStock() != 1) {
            return;
        }
        if (hasActive(food.getUserId(), food.getId(), null, "low_stock")) {
            return;
        }
        Reminder r = new Reminder();
        r.setUserId(food.getUserId());
        r.setFoodItemId(food.getId());
        r.setType("low_stock");
        r.setTitle("库存不足");
        r.setContent(String.format("「%s」当前库存 %s %s，低于设定阈值，建议补充购买。",
                food.getName(), food.getQuantity(), food.getUnit()));
        r.setRemindTime(LocalDateTime.now());
        r.setIsRead(0);
        r.setStatus("active");
        reminderMapper.insert(r);
    }

    public void zoneAbnormal(FridgeZone zone, ZoneRecord record) {
        if (hasActive(zone.getUserId(), null, zone.getId(), "zone_abnormal")) {
            return;
        }
        Reminder r = new Reminder();
        r.setUserId(zone.getUserId());
        r.setZoneId(zone.getId());
        r.setType("zone_abnormal");
        r.setTitle("冰箱状态异常");
        r.setContent(String.format("「%s」当前温度 %s℃、湿度 %s%%，超出建议范围，请检查冰箱状态；该分区食材的保质期估算可能不准确。",
                zone.getName(), record.getTempC(), record.getHumidity() == null ? "未知" : record.getHumidity()));
        r.setRemindTime(LocalDateTime.now());
        r.setIsRead(0);
        r.setStatus("active");
        reminderMapper.insert(r);
    }

    private Reminder owned(Long id) {
        Reminder r = reminderMapper.selectById(id);
        if (r == null || !r.getUserId().equals(UserContext.get())) {
            throw new BusinessException(404, "提醒不存在");
        }
        return r;
    }

    private boolean hasActive(Long userId, Long foodItemId, Long zoneId, String type) {
        LambdaQueryWrapper<Reminder> qw = new LambdaQueryWrapper<Reminder>()
                .eq(Reminder::getUserId, userId)
                .eq(Reminder::getType, type)
                .eq(Reminder::getStatus, "active");
        if (foodItemId != null) {
            qw.eq(Reminder::getFoodItemId, foodItemId);
        }
        if (zoneId != null) {
            qw.eq(Reminder::getZoneId, zoneId);
        }
        return reminderMapper.selectCount(qw) > 0;
    }
}
