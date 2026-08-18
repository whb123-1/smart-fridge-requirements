package com.xianzhi.fridge.telemetry.application;

import com.xianzhi.fridge.fridge.domain.SensorMetric;
import com.xianzhi.fridge.fridge.infrastructure.Fridge;
import com.xianzhi.fridge.fridge.infrastructure.FridgeRepository;
import com.xianzhi.fridge.fridge.infrastructure.FridgeZone;
import com.xianzhi.fridge.fridge.infrastructure.FridgeZoneRepository;
import com.xianzhi.fridge.fridge.infrastructure.SensorSlot;
import com.xianzhi.fridge.fridge.infrastructure.SensorSlotRepository;
import com.xianzhi.fridge.identity.domain.TemperatureUnit;
import com.xianzhi.fridge.identity.infrastructure.AppUser;
import com.xianzhi.fridge.identity.infrastructure.AppUserRepository;
import com.xianzhi.fridge.inventory.domain.BatchStatus;
import com.xianzhi.fridge.inventory.infrastructure.InventoryBatchRepository;
import com.xianzhi.fridge.shared.web.ApiException;
import com.xianzhi.fridge.telemetry.api.EnvironmentContracts;
import com.xianzhi.fridge.telemetry.domain.EnvironmentStatus;
import com.xianzhi.fridge.telemetry.domain.IncidentReason;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnvironmentQueryService {
    private final FridgeRepository fridges; private final FridgeZoneRepository zones; private final SensorSlotRepository sensors;
    private final ZoneEnvironmentStateRepository states; private final EnvironmentIncidentRepository incidents;
    private final SensorReadingStore readings; private final AppUserRepository users; private final InventoryBatchRepository batches; private final Clock clock;
    public EnvironmentQueryService(FridgeRepository fridges, FridgeZoneRepository zones, SensorSlotRepository sensors,
                                   ZoneEnvironmentStateRepository states, EnvironmentIncidentRepository incidents,
                                   SensorReadingStore readings, AppUserRepository users, InventoryBatchRepository batches, Clock clock) {
        this.fridges = fridges; this.zones = zones; this.sensors = sensors; this.states = states; this.incidents = incidents;
        this.readings = readings; this.users = users; this.batches = batches; this.clock = clock;
    }
    @Transactional(readOnly = true)
    public List<EnvironmentContracts.ReadingView> readings(UUID userId, UUID zoneId, SensorMetric metric, Instant from, Instant to, Integer limit) {
        FridgeZone zone = ownedZone(userId, zoneId); Instant end = to == null ? clock.instant() : to;
        Instant start = from == null ? end.minus(Duration.ofHours(24)) : from;
        if (start.isAfter(end)) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "from must not be after to");
        int size = Math.max(1, Math.min(limit == null ? 500 : limit, 1000));
        return readings.readings(userId, zone.getId(), metric, start, end, size).stream().map(value ->
                new EnvironmentContracts.ReadingView(value.id(), value.sensorId(), value.metric(), value.value(), value.unit(), value.quality(), value.source(), value.observedAt(), value.receivedAt())).toList();
    }
    @Transactional(readOnly = true)
    public EnvironmentContracts.FridgeView environment(UUID userId, UUID fridgeId) {
        Fridge fridge = ownedFridge(userId, fridgeId); AppUser user = users.findById(userId).orElseThrow();
        List<FridgeZone> ownedZones = zones.findByFridgeIdInAndDeletedAtIsNullOrderByCreatedAtAsc(List.of(fridgeId));
        Map<UUID, ZoneEnvironmentState> stateMap = states.findByFridgeIdOrderByZoneIdAscMetricAsc(fridgeId).stream()
                .collect(Collectors.toMap(value -> key(value.getZoneId(), value.getMetric()), Function.identity()));
        List<EnvironmentIncident> active = incidents.findByFridgeIdAndStatusOrderByStartedAtDesc(fridgeId, "OPEN");
        List<EnvironmentContracts.ZoneView> views = ownedZones.stream().map(zone -> zoneView(user, zone, stateMap, active)).toList();
        int bound = views.stream().mapToInt(EnvironmentContracts.ZoneView::boundSensorCount).sum();
        int online = views.stream().mapToInt(EnvironmentContracts.ZoneView::onlineSensorCount).sum();
        int stale = views.stream().mapToInt(EnvironmentContracts.ZoneView::staleSensorCount).sum();
        Instant synced = views.stream().flatMap(value -> value.metrics().stream()).map(EnvironmentContracts.MetricView::lastReceivedAt)
                .filter(java.util.Objects::nonNull).max(Instant::compareTo).orElse(null);
        return new EnvironmentContracts.FridgeView(fridgeId, fridge.getName(), user.getTemperatureUnit().name(), bound, online, stale, synced, views);
    }
    private EnvironmentContracts.ZoneView zoneView(AppUser user, FridgeZone zone, Map<UUID, ZoneEnvironmentState> stateMap,
                                                    List<EnvironmentIncident> allIncidents) {
        Instant cutoff = clock.instant().minus(Duration.ofMinutes(15));
        List<SensorSlot> bound = sensors.findByZoneIdAndEnabledTrueOrderBySlotIndexAsc(zone.getId()).stream().filter(value -> "BOUND".equals(value.getBindingStatus())).toList();
        int online = (int) bound.stream().filter(value -> "GOOD".equals(value.getLastQuality()) && value.getLastObservedAt() != null && !value.getLastObservedAt().isBefore(cutoff)).count();
        List<EnvironmentIncident> zoneIncidents = allIncidents.stream().filter(value -> zone.getId().equals(value.getZoneId())).toList();
        List<EnvironmentContracts.MetricView> metrics = java.util.Arrays.stream(SensorMetric.values()).map(metric ->
                metricView(user.getTemperatureUnit(), zone, metric, stateMap.get(key(zone.getId(), metric)),
                        zoneIncidents.stream().filter(value -> value.getMetric() == metric).toList(),
                        bound.stream().anyMatch(value -> value.getMetric() == metric))).toList();
        EnvironmentStatus status = metrics.stream().map(EnvironmentContracts.MetricView::status).max(Comparator.comparingInt(this::rank)).orElse(EnvironmentStatus.NO_SENSOR);
        List<EnvironmentContracts.IncidentView> incidentViews = zoneIncidents.stream().map(this::incidentView).toList();
        List<UUID> affected = batches.findByZoneIdAndStatus(zone.getId(), BatchStatus.ACTIVE).stream().map(value -> value.getId()).toList();
        return new EnvironmentContracts.ZoneView(zone.getId(), zone.getName(), zone.getKind().name(), status, bound.size(), online, bound.size() - online, metrics, incidentViews, affected);
    }
    private EnvironmentContracts.MetricView metricView(TemperatureUnit preference, FridgeZone zone, SensorMetric metric,
                                                        ZoneEnvironmentState state, List<EnvironmentIncident> incidents, boolean hasBound) {
        EnvironmentStatus status;
        if (!hasBound) status = EnvironmentStatus.NO_SENSOR;
        else if (incidents.stream().anyMatch(value -> value.getReason() == IncidentReason.STALE_DATA) || state == null || "STALE".equals(state.getCurrentQuality())) status = EnvironmentStatus.STALE;
        else if (incidents.stream().anyMatch(value -> value.getReason() == IncidentReason.OUT_OF_RANGE
                || value.getReason() == IncidentReason.SENSOR_SUSPECT) || state.getOutsideSince() != null) status = EnvironmentStatus.WARNING;
        else status = EnvironmentStatus.NORMAL;
        BigDecimal raw = state == null ? null : state.getCurrentValue();
        boolean fahrenheit = metric == SensorMetric.TEMPERATURE && preference == TemperatureUnit.F;
        BigDecimal display = raw == null ? null : fahrenheit ? toFahrenheit(raw) : raw;
        BigDecimal rawMin = metric == SensorMetric.TEMPERATURE ? zone.getSafeTemperatureMinC() : zone.getSafeHumidityMinPct();
        BigDecimal rawMax = metric == SensorMetric.TEMPERATURE ? zone.getSafeTemperatureMaxC() : zone.getSafeHumidityMaxPct();
        String unit = metric == SensorMetric.TEMPERATURE ? (fahrenheit ? "F" : "C") : "PERCENT";
        BigDecimal min = fahrenheit ? toFahrenheit(rawMin) : rawMin; BigDecimal max = fahrenheit ? toFahrenheit(rawMax) : rawMax;
        Instant since = status == EnvironmentStatus.WARNING && state != null ? state.getOutsideSince()
                : status == EnvironmentStatus.STALE && state != null ? state.getStaleSince() : state == null ? null : state.getNormalSince();
        return new EnvironmentContracts.MetricView(metric, status, metric == SensorMetric.TEMPERATURE ? raw : null,
                display, unit, state == null ? null : state.getCurrentQuality(), new EnvironmentContracts.SafeRange(min, max, unit),
                state == null ? null : state.getLastObservedAt(), state == null ? null : state.getLastReceivedAt(), since);
    }
    private EnvironmentContracts.IncidentView incidentView(EnvironmentIncident value) { return new EnvironmentContracts.IncidentView(value.getId(), value.getMetric(), value.getReason().name(), value.getDirection(), value.getSeverity().name(), value.getStartedAt(), value.getLastObservedAt(), value.getMaxDeviation()); }
    private FridgeZone ownedZone(UUID userId, UUID zoneId) { FridgeZone zone = zones.findById(zoneId).filter(value -> value.getDeletedAt() == null).orElseThrow(() -> notFound("ZONE_NOT_FOUND")); ownedFridge(userId, zone.getFridgeId()); return zone; }
    private Fridge ownedFridge(UUID userId, UUID fridgeId) { return fridges.findById(fridgeId).filter(value -> userId.equals(value.getUserId()) && value.getDeletedAt() == null).orElseThrow(() -> notFound("FRIDGE_NOT_FOUND")); }
    private ApiException notFound(String code) { return new ApiException(HttpStatus.NOT_FOUND, code, "Resource not found"); }
    private UUID key(UUID zoneId, SensorMetric metric) { return UUID.nameUUIDFromBytes((zoneId + ":" + metric.name()).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    private int rank(EnvironmentStatus status) { return switch (status) { case WARNING -> 4; case STALE -> 3; case NORMAL -> 2; case NO_SENSOR -> 1; }; }
    private static BigDecimal toFahrenheit(BigDecimal celsius) { return celsius.multiply(BigDecimal.valueOf(9)).divide(BigDecimal.valueOf(5), 2, RoundingMode.HALF_UP).add(BigDecimal.valueOf(32)); }
}
