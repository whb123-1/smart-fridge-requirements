package com.smartfridge.module.zone.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartfridge.common.BusinessException;
import com.smartfridge.common.UserContext;
import com.smartfridge.module.food.entity.FoodItem;
import com.smartfridge.module.food.mapper.FoodItemMapper;
import com.smartfridge.module.reminder.service.ReminderService;
import com.smartfridge.module.zone.entity.FridgeZone;
import com.smartfridge.module.zone.entity.ZoneRecord;
import com.smartfridge.module.zone.mapper.FridgeZoneMapper;
import com.smartfridge.module.zone.mapper.ZoneRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ZoneService {

    private final FridgeZoneMapper zoneMapper;
    private final ZoneRecordMapper recordMapper;
    private final ReminderService reminderService;
    private final FoodItemMapper foodItemMapper;

    public List<ZoneVO> list() {
        List<FridgeZone> zones = zoneMapper.selectList(new LambdaQueryWrapper<FridgeZone>()
                .eq(FridgeZone::getUserId, UserContext.get())
                .orderByAsc(FridgeZone::getSort)
                .orderByAsc(FridgeZone::getId));
        return zones.stream().map(this::toVO).toList();
    }

    public FridgeZone create(ZoneReq req) {
        FridgeZone zone = new FridgeZone();
        zone.setUserId(UserContext.get());
        fill(zone, req);
        zone.setStatus("normal");
        zoneMapper.insert(zone);
        return zone;
    }

    public FridgeZone update(Long id, ZoneReq req) {
        FridgeZone zone = owned(id);
        fill(zone, req);
        zoneMapper.updateById(zone);
        return zone;
    }

    public void delete(Long id) {
        zoneMapper.deleteById(owned(id).getId());
    }

    public ZoneRecord record(Long zoneId, RecordReq req) {
        FridgeZone zone = owned(zoneId);
        double tempC = req.temp();
        if ("F".equalsIgnoreCase(zone.getTempUnit())) {
            tempC = (tempC - 32) * 5 / 9;
        }
        LocalDateTime now = LocalDateTime.now();
        ZoneRecord record = new ZoneRecord();
        record.setZoneId(zoneId);
        record.setTempC(BigDecimal.valueOf(tempC).setScale(1, RoundingMode.HALF_UP));
        record.setHumidity(req.humidity() == null ? null : BigDecimal.valueOf(req.humidity()));
        record.setSource(req.source() == null ? "manual" : req.source());
        record.setRecordTime(req.recordTime() == null ? now : req.recordTime());

        boolean abnormal = isAbnormal(zone, tempC, req.humidity());
        ZoneRecord last = latest(zoneId);
        int abnormalSeconds = 0;
        if (abnormal && last != null && last.getAbnormalSeconds() != null && last.getAbnormalSeconds() >= 0) {
            long elapsed = Duration.between(last.getRecordTime(), record.getRecordTime()).getSeconds();
            abnormalSeconds = last.getAbnormalSeconds() + (int) Math.max(0, elapsed);
        }
        record.setAbnormalSeconds(abnormalSeconds);
        recordMapper.insert(record);

        String prevStatus = zone.getStatus();
        zone.setStatus(abnormal ? "abnormal" : "normal");
        zone.setLastRecordAt(now);
        zoneMapper.updateById(zone);
        if (abnormal && !"abnormal".equals(prevStatus)) {
            reminderService.zoneAbnormal(zone, record);
        }
        return record;
    }

    public List<ZoneRecord> records(Long zoneId, LocalDate from, LocalDate to) {
        owned(zoneId);
        LambdaQueryWrapper<ZoneRecord> qw = new LambdaQueryWrapper<ZoneRecord>()
                .eq(ZoneRecord::getZoneId, zoneId);
        if (from != null) {
            qw.ge(ZoneRecord::getRecordTime, from.atStartOfDay());
        }
        if (to != null) {
            qw.le(ZoneRecord::getRecordTime, to.plusDays(1).atStartOfDay());
        }
        qw.orderByDesc(ZoneRecord::getRecordTime).last("LIMIT 500");
        return recordMapper.selectList(qw);
    }

    /**
     * 各分区的提醒汇总：临期 / 过期 / 低库存 / 温湿度异常
     */
    public List<ZoneAlert> alerts() {
        Long userId = UserContext.get();
        List<FridgeZone> zones = zoneMapper.selectList(new LambdaQueryWrapper<FridgeZone>()
                .eq(FridgeZone::getUserId, userId));
        LocalDate today = LocalDate.now();
        List<ZoneAlert> result = new ArrayList<>();
        for (FridgeZone zone : zones) {
            List<FoodItem> foods = foodItemMapper.selectList(new LambdaQueryWrapper<FoodItem>()
                    .eq(FoodItem::getUserId, userId)
                    .eq(FoodItem::getZoneId, zone.getId()));
            long expireSoon = 0;
            long expired = 0;
            long lowStock = 0;
            boolean abnormal = "abnormal".equals(zone.getStatus()) || "stale".equals(zone.getStatus());
            for (FoodItem food : foods) {
                if (food.getIsLowStock() != null && food.getIsLowStock() == 1) {
                    lowStock++;
                }
                if ("expired".equals(food.getStatus())) {
                    expired++;
                    continue;
                }
                if (!"in_stock".equals(food.getStatus())) {
                    continue;
                }
                LocalDate expiry = food.getSuggestedExpiryDate();
                if (expiry != null && expiry.isBefore(today)) {
                    expired++;
                } else if (expiry != null && !expiry.isAfter(today.plusDays(3))) {
                    expireSoon++;
                }
            }
            result.add(new ZoneAlert(zone.getId(), zone.getZoneType(), zone.getName(),
                    expireSoon, expired, lowStock, abnormal));
        }
        return result;
    }

    private boolean isAbnormal(FridgeZone zone, double tempC, Double humidity) {
        double min = zone.getMinTemp() != null ? zone.getMinTemp().doubleValue() : defaultMin(zone.getZoneType());
        double max = zone.getMaxTemp() != null ? zone.getMaxTemp().doubleValue() : defaultMax(zone.getZoneType());
        boolean tempAbnormal = tempC < min || tempC > max;
        boolean humidityAbnormal = humidity != null
                && ((zone.getMinHumidity() != null && humidity < zone.getMinHumidity().doubleValue())
                || (zone.getMaxHumidity() != null && humidity > zone.getMaxHumidity().doubleValue()));
        return tempAbnormal || humidityAbnormal;
    }

    private ZoneVO toVO(FridgeZone zone) {
        ZoneRecord latest = latest(zone.getId());
        String status = zone.getStatus() == null ? "normal" : zone.getStatus();
        String statusText = "正常";
        if (zone.getLastRecordAt() != null
                && zone.getLastRecordAt().plusMinutes(30).isBefore(LocalDateTime.now())) {
            status = "stale";
            statusText = "数据长时间未更新";
        } else if ("abnormal".equals(status)) {
            statusText = "温湿度异常";
        }
        return new ZoneVO(zone.getId(), zone.getName(), zone.getZoneType(),
                zone.getTargetTemp(), zone.getTargetHumidity(), zone.getTempUnit(),
                zone.getMinTemp(), zone.getMaxTemp(), zone.getMinHumidity(), zone.getMaxHumidity(),
                status, statusText, zone.getLastRecordAt(),
                latest == null ? null : latest.getTempC(),
                latest == null ? null : latest.getHumidity(),
                latest == null ? 0 : latest.getAbnormalSeconds());
    }

    private ZoneRecord latest(Long zoneId) {
        return recordMapper.selectOne(new LambdaQueryWrapper<ZoneRecord>()
                .eq(ZoneRecord::getZoneId, zoneId)
                .orderByDesc(ZoneRecord::getRecordTime)
                .last("LIMIT 1"));
    }

    private FridgeZone owned(Long id) {
        FridgeZone zone = zoneMapper.selectById(id);
        if (zone == null || !zone.getUserId().equals(UserContext.get())) {
            throw new BusinessException(404, "分区不存在");
        }
        return zone;
    }

    private void fill(FridgeZone zone, ZoneReq req) {
        zone.setName(req.name());
        zone.setZoneType(req.zoneType() == null ? "冷藏区" : req.zoneType());
        zone.setTargetTemp(req.targetTemp());
        zone.setTargetHumidity(req.targetHumidity());
        zone.setTempUnit(req.tempUnit() == null ? "C" : req.tempUnit());
        zone.setMinTemp(req.minTemp());
        zone.setMaxTemp(req.maxTemp());
        zone.setMinHumidity(req.minHumidity());
        zone.setMaxHumidity(req.maxHumidity());
        zone.setSort(req.sort() == null ? 0 : req.sort());
    }

    private double defaultMin(String zoneType) {
        if ("冷冻区".equals(zoneType)) return -18;
        if ("变温区".equals(zoneType)) return -3;
        if ("保鲜区".equals(zoneType)) return 0;
        if ("常温区".equals(zoneType)) return 10;
        return 0;
    }

    private double defaultMax(String zoneType) {
        if ("冷冻区".equals(zoneType)) return -12;
        if ("变温区".equals(zoneType)) return 4;
        if ("保鲜区".equals(zoneType)) return 4;
        if ("常温区".equals(zoneType)) return 25;
        return 8;
    }

    public record ZoneReq(
            String name,
            String zoneType,
            BigDecimal targetTemp,
            BigDecimal targetHumidity,
            String tempUnit,
            BigDecimal minTemp,
            BigDecimal maxTemp,
            BigDecimal minHumidity,
            BigDecimal maxHumidity,
            Integer sort) {
    }

    public record RecordReq(
            Double temp,
            Double humidity,
            String source,
            LocalDateTime recordTime) {
    }

    public record ZoneVO(
            Long id,
            String name,
            String zoneType,
            BigDecimal targetTemp,
            BigDecimal targetHumidity,
            String tempUnit,
            BigDecimal minTemp,
            BigDecimal maxTemp,
            BigDecimal minHumidity,
            BigDecimal maxHumidity,
            String status,
            String statusText,
            LocalDateTime lastRecordAt,
            BigDecimal latestTempC,
            BigDecimal latestHumidity,
            Integer abnormalSeconds) {
    }

    public record ZoneAlert(Long zoneId, String zoneType, String zoneName,
                            long expireSoon, long expired, long lowStock, boolean abnormal) {
    }
}
