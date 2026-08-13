package com.smartfridge.module.food.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartfridge.module.food.entity.FoodCategory;
import com.smartfridge.module.food.entity.FoodItem;
import com.smartfridge.module.food.mapper.FoodCategoryMapper;
import com.smartfridge.module.zone.entity.FridgeZone;
import com.smartfridge.module.zone.entity.ZoneRecord;
import com.smartfridge.module.zone.mapper.FridgeZoneMapper;
import com.smartfridge.module.zone.mapper.ZoneRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ExpiryService {

    private final FoodCategoryMapper categoryMapper;
    private final FridgeZoneMapper zoneMapper;
    private final ZoneRecordMapper recordMapper;

    /**
     * 根据分类、开封状态与所在分区温湿度，计算并写回建议食用期限。
     */
    public void apply(FoodItem item) {
        if (item.getCategoryId() == null) {
            return;
        }
        FoodCategory cat = categoryMapper.selectById(item.getCategoryId());
        if (cat == null) {
            return;
        }
        int baseDays = cat.getShelfLifeDays() != null ? cat.getShelfLifeDays() : 1;
        if (item.getOpenedDate() != null) {
            baseDays = cat.getOpenedDays() != null ? cat.getOpenedDays() : Math.max(1, baseDays / 2);
        }
        LocalDate from = item.getOpenedDate() != null ? item.getOpenedDate() : item.getEntryDate();
        ZoneFactor zf = zoneFactor(item.getZoneId());
        int days = Math.max(1, (int) Math.round(baseDays * zf.factor()));
        LocalDate suggested = from.plusDays(days);
        String basis = zf.usedZoneData() ? "按参考温湿度估算" : "系统估算";
        if (item.getPackageExpiryDate() != null && item.getPackageExpiryDate().isBefore(suggested)) {
            suggested = item.getPackageExpiryDate();
            basis = "包装标注";
        }
        item.setSuggestedExpiryDate(suggested);
        item.setExpiryBasis(basis);
    }

    private ZoneFactor zoneFactor(Long zoneId) {
        if (zoneId == null) {
            return new ZoneFactor(1.0, false);
        }
        FridgeZone zone = zoneMapper.selectById(zoneId);
        if (zone == null || zone.getLastRecordAt() == null
                || zone.getLastRecordAt().plusMinutes(30).isBefore(LocalDateTime.now())) {
            // 无有效温湿度数据：按参考温湿度估算
            return new ZoneFactor(1.0, false);
        }
        ZoneRecord latest = recordMapper.selectOne(new LambdaQueryWrapper<ZoneRecord>()
                .eq(ZoneRecord::getZoneId, zoneId)
                .orderByDesc(ZoneRecord::getRecordTime)
                .last("LIMIT 1"));
        if (latest == null) {
            return new ZoneFactor(1.0, false);
        }
        double factor = 1.0;
        double temp = latest.getTempC().doubleValue();
        double min = zone.getMinTemp() != null ? zone.getMinTemp().doubleValue() : defaultMin(zone.getZoneType());
        double max = zone.getMaxTemp() != null ? zone.getMaxTemp().doubleValue() : defaultMax(zone.getZoneType());
        if (temp < min || temp > max) {
            double deviation = Math.max(min - temp, temp - max);
            factor = deviation <= 3 ? 0.8 : deviation <= 6 ? 0.6 : 0.4;
        }
        if (latest.getHumidity() != null) {
            double h = latest.getHumidity().doubleValue();
            if (zone.getMinHumidity() != null && h < zone.getMinHumidity().doubleValue()) {
                factor *= 0.9;
            }
            if (zone.getMaxHumidity() != null && h > zone.getMaxHumidity().doubleValue()) {
                factor *= 0.9;
            }
        }
        return new ZoneFactor(Math.max(0.3, factor), true);
    }

    private double defaultMin(String zoneType) {
        if ("冷冻区".equals(zoneType)) return -18;
        if ("变温区".equals(zoneType)) return -3;
        if ("保鲜区".equals(zoneType)) return 0;
        if ("常温区".equals(zoneType)) return 10;
        return 0; // 冷藏区/自定义
    }

    private double defaultMax(String zoneType) {
        if ("冷冻区".equals(zoneType)) return -12;
        if ("变温区".equals(zoneType)) return 4;
        if ("保鲜区".equals(zoneType)) return 4;
        if ("常温区".equals(zoneType)) return 25;
        return 8; // 冷藏区/自定义
    }

    private record ZoneFactor(double factor, boolean usedZoneData) {
    }
}
