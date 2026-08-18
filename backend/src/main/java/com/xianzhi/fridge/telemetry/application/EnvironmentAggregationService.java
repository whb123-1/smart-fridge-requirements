package com.xianzhi.fridge.telemetry.application;

import com.xianzhi.fridge.fridge.domain.SensorMetric;
import com.xianzhi.fridge.fridge.infrastructure.Fridge;
import com.xianzhi.fridge.fridge.infrastructure.FridgeRepository;
import com.xianzhi.fridge.fridge.infrastructure.FridgeZone;
import com.xianzhi.fridge.fridge.infrastructure.FridgeZoneRepository;
import com.xianzhi.fridge.fridge.infrastructure.SensorSlot;
import com.xianzhi.fridge.fridge.infrastructure.SensorSlotRepository;
import com.xianzhi.fridge.notification.application.NotificationService;
import com.xianzhi.fridge.shared.domain.UuidV7;
import com.xianzhi.fridge.telemetry.domain.IncidentReason;
import com.xianzhi.fridge.telemetry.domain.IncidentSeverity;
import com.xianzhi.fridge.telemetry.infrastructure.EnvironmentIncident;
import com.xianzhi.fridge.telemetry.infrastructure.EnvironmentIncidentRepository;
import com.xianzhi.fridge.telemetry.infrastructure.SensorReadingStore;
import com.xianzhi.fridge.telemetry.infrastructure.ZoneEnvironmentState;
import com.xianzhi.fridge.telemetry.infrastructure.ZoneEnvironmentStateRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnvironmentAggregationService {
    private static final Duration FRESH_WINDOW = Duration.ofMinutes(15);
    private static final Duration CONFIRMATION = Duration.ofMinutes(15);
    private final FridgeZoneRepository zones;
    private final FridgeRepository fridges;
    private final SensorSlotRepository sensors;
    private final SensorReadingStore readings;
    private final ZoneEnvironmentStateRepository states;
    private final EnvironmentIncidentRepository incidents;
    private final NotificationService notifications;
    private final ShelfLifeRiskService risk;
    private final Clock clock;
    public EnvironmentAggregationService(FridgeZoneRepository zones, FridgeRepository fridges,
                                         SensorSlotRepository sensors, SensorReadingStore readings,
                                         ZoneEnvironmentStateRepository states, EnvironmentIncidentRepository incidents,
                                         NotificationService notifications, ShelfLifeRiskService risk, Clock clock) {
        this.zones = zones; this.fridges = fridges; this.sensors = sensors; this.readings = readings;
        this.states = states; this.incidents = incidents; this.notifications = notifications; this.risk = risk; this.clock = clock;
    }

    @Transactional
    public void aggregateAll() {
        Instant now = clock.instant();
        for (FridgeZone zone : zones.findByEnabledTrueAndDeletedAtIsNullOrderByCreatedAtAsc()) {
            Fridge fridge = fridges.findById(zone.getFridgeId()).filter(value -> value.getDeletedAt() == null).orElse(null);
            if (fridge != null) aggregateZone(fridge, zone, now);
        }
        risk.accumulate();
    }

    private void aggregateZone(Fridge fridge, FridgeZone zone, Instant now) {
        List<SensorSlot> bound = sensors.findByZoneIdAndEnabledTrueOrderBySlotIndexAsc(zone.getId()).stream()
                .filter(value -> "BOUND".equals(value.getBindingStatus())).toList();
        List<SensorReadingStore.AggregateRow> fresh = readings.latestGoodByZone(zone.getId(), now.minus(FRESH_WINDOW));
        Set<UUID> boundIds = new HashSet<>(); bound.forEach(value -> boundIds.add(value.getId()));
        fresh = fresh.stream().filter(value -> boundIds.contains(value.sensorId())).toList();
        for (SensorMetric metric : SensorMetric.values()) aggregateMetric(fridge, zone, metric,
                bound.stream().filter(value -> value.getMetric() == metric).toList(),
                fresh.stream().filter(value -> value.metric() == metric).toList(), now);
    }

    private void aggregateMetric(Fridge fridge, FridgeZone zone, SensorMetric metric, List<SensorSlot> bound,
                                 List<SensorReadingStore.AggregateRow> fresh, Instant now) {
        ZoneEnvironmentState state = states.findByZoneIdAndMetric(zone.getId(), metric)
                .orElseGet(() -> new ZoneEnvironmentState(UuidV7.next(), fridge.getUserId(), fridge.getId(), zone.getId(), metric, now));
        if (bound.isEmpty()) {
            state.noSensor(now); states.save(state);
            close(zone, metric, IncidentReason.STALE_DATA, now);
            close(zone, metric, IncidentReason.OUT_OF_RANGE, now);
            close(zone, metric, IncidentReason.SENSOR_SUSPECT, now);
            return;
        }
        if (fresh.isEmpty()) {
            Instant last = bound.stream().map(SensorSlot::getLastObservedAt).filter(java.util.Objects::nonNull)
                    .max(Instant::compareTo).orElse(now.minus(FRESH_WINDOW));
            Instant staleSince = last.plus(FRESH_WINDOW); if (staleSince.isAfter(now)) staleSince = now;
            state.stale(staleSince, now); states.save(state);
            EnvironmentIncident incident = open(fridge, zone, metric, IncidentReason.STALE_DATA, "NONE", IncidentSeverity.MILD,
                    staleSince, now, BigDecimal.ZERO, now);
            notifications.ensureIncident(incident, zone.getName() + "传感器数据已陈旧", "超过 15 分钟没有收到有效的" + label(metric) + "读数，请检查设备连接。");
            return;
        }
        BigDecimal average = fresh.stream().map(SensorReadingStore.AggregateRow::value).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(fresh.size()), 3, RoundingMode.HALF_UP);
        Instant observed = fresh.stream().map(SensorReadingStore.AggregateRow::observedAt).max(Instant::compareTo).orElse(now);
        Instant received = fresh.stream().map(SensorReadingStore.AggregateRow::receivedAt).max(Instant::compareTo).orElse(now);
        close(zone, metric, IncidentReason.STALE_DATA, now);
        BigDecimal min = metric == SensorMetric.TEMPERATURE ? zone.getSafeTemperatureMinC() : zone.getSafeHumidityMinPct();
        BigDecimal max = metric == SensorMetric.TEMPERATURE ? zone.getSafeTemperatureMaxC() : zone.getSafeHumidityMaxPct();
        boolean outside = average.compareTo(min) < 0 || average.compareTo(max) > 0;
        state.measured(average, observed, received, outside, now); states.save(state);
        if (outside) {
            BigDecimal deviation = average.compareTo(min) < 0 ? min.subtract(average) : average.subtract(max);
            String direction = average.compareTo(min) < 0 ? "LOW" : "HIGH";
            if (confirmed(state.getOutsideSince(), now)) {
                IncidentSeverity severity = severity(metric, deviation);
                EnvironmentIncident incident = open(fridge, zone, metric, IncidentReason.OUT_OF_RANGE, direction, severity,
                        state.getOutsideSince(), observed, deviation, now);
                notifications.ensureIncident(incident, zone.getName() + label(metric) + "异常", "当前" + label(metric) + "已连续偏离安全范围，偏差 " + deviation.stripTrailingZeros().toPlainString() + unit(metric) + "。");
            }
        } else if (confirmed(state.getNormalSince(), now)) {
            close(zone, metric, IncidentReason.OUT_OF_RANGE, now); close(zone, metric, IncidentReason.STALE_DATA, now);
        }
    }

    @Transactional
    public void sensorSuspect(UUID sensorId) {
        Instant now = clock.instant(); SensorSlot sensor = sensors.findById(sensorId).orElseThrow();
        if (!"BOUND".equals(sensor.getBindingStatus()) || !sensor.isEnabled() || sensor.getConsecutiveSuspectCount() < 3) return;
        FridgeZone zone = zones.findById(sensor.getZoneId()).orElseThrow(); Fridge fridge = fridges.findById(zone.getFridgeId()).orElseThrow();
        EnvironmentIncident incident = open(fridge, zone, sensor.getMetric(), IncidentReason.SENSOR_SUSPECT, "NONE", IncidentSeverity.MODERATE, now, now, BigDecimal.ZERO, now);
        notifications.ensureIncident(incident, zone.getName() + "传感器读数可疑", "传感器连续三次超过最大变化速率，读数已保留但未参与环境与保质期计算。");
    }

    @Transactional
    public void sensorRecovered(UUID sensorId) {
        Instant now = clock.instant(); SensorSlot sensor = sensors.findById(sensorId).orElseThrow();
        boolean stillSuspect = sensors.findByZoneIdAndEnabledTrueOrderBySlotIndexAsc(sensor.getZoneId()).stream()
                .anyMatch(value -> value.getMetric() == sensor.getMetric()
                        && "BOUND".equals(value.getBindingStatus())
                        && value.getConsecutiveSuspectCount() >= 3);
        if (!stillSuspect) close(zones.findById(sensor.getZoneId()).orElseThrow(), sensor.getMetric(), IncidentReason.SENSOR_SUSPECT, now);
    }

    private EnvironmentIncident open(Fridge fridge, FridgeZone zone, SensorMetric metric, IncidentReason reason,
                                     String direction, IncidentSeverity severity, Instant startedAt,
                                     Instant observedAt, BigDecimal deviation, Instant now) {
        EnvironmentIncident incident = incidents.findByZoneIdAndMetricAndReasonAndStatus(zone.getId(), metric, reason, "OPEN").orElse(null);
        if (incident == null) incident = new EnvironmentIncident(UuidV7.next(), fridge.getUserId(), fridge.getId(), zone.getId(), metric, reason, direction, severity, startedAt, observedAt, deviation, now);
        else incident.observe(direction, severity, deviation, observedAt, now);
        return incidents.save(incident);
    }
    private void close(FridgeZone zone, SensorMetric metric, IncidentReason reason, Instant now) {
        incidents.findByZoneIdAndMetricAndReasonAndStatus(zone.getId(), metric, reason, "OPEN").ifPresent(value -> { value.close(now); incidents.save(value); notifications.resolveIncident(value); });
    }
    private static boolean confirmed(Instant since, Instant now) { return since != null && !since.plus(CONFIRMATION).isAfter(now); }
    private static IncidentSeverity severity(SensorMetric metric, BigDecimal deviation) {
        BigDecimal moderate = metric == SensorMetric.TEMPERATURE ? BigDecimal.valueOf(2) : BigDecimal.valueOf(10);
        BigDecimal severe = metric == SensorMetric.TEMPERATURE ? BigDecimal.valueOf(5) : BigDecimal.valueOf(20);
        return deviation.compareTo(severe) >= 0 ? IncidentSeverity.SEVERE : deviation.compareTo(moderate) >= 0 ? IncidentSeverity.MODERATE : IncidentSeverity.MILD;
    }
    private static String label(SensorMetric metric) { return metric == SensorMetric.TEMPERATURE ? "温度" : "湿度"; }
    private static String unit(SensorMetric metric) { return metric == SensorMetric.TEMPERATURE ? "℃" : "%"; }
}
