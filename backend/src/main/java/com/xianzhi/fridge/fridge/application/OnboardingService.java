package com.xianzhi.fridge.fridge.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianzhi.fridge.fridge.api.OnboardingContracts;
import com.xianzhi.fridge.fridge.domain.SensorMetric;
import com.xianzhi.fridge.fridge.domain.ZoneDefaults;
import com.xianzhi.fridge.fridge.domain.ZoneKind;
import com.xianzhi.fridge.fridge.infrastructure.Fridge;
import com.xianzhi.fridge.fridge.infrastructure.FridgeRepository;
import com.xianzhi.fridge.fridge.infrastructure.FridgeZone;
import com.xianzhi.fridge.fridge.infrastructure.FridgeZoneRepository;
import com.xianzhi.fridge.fridge.infrastructure.Device;
import com.xianzhi.fridge.fridge.infrastructure.DeviceRepository;
import com.xianzhi.fridge.fridge.infrastructure.SensorProfileRepository;
import com.xianzhi.fridge.fridge.infrastructure.SensorSlot;
import com.xianzhi.fridge.fridge.infrastructure.SensorSlotRepository;
import com.xianzhi.fridge.identity.infrastructure.AppUser;
import com.xianzhi.fridge.identity.infrastructure.AppUserRepository;
import com.xianzhi.fridge.shared.application.AuditService;
import com.xianzhi.fridge.shared.domain.Hashing;
import com.xianzhi.fridge.shared.domain.UuidV7;
import com.xianzhi.fridge.shared.infrastructure.IdempotencyRecord;
import com.xianzhi.fridge.shared.infrastructure.IdempotencyRecordRepository;
import com.xianzhi.fridge.shared.web.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnboardingService {
    private final AppUserRepository users;
    private final FridgeRepository fridges;
    private final FridgeZoneRepository zones;
    private final DeviceRepository devices;
    private final SensorProfileRepository profiles;
    private final SensorSlotRepository sensors;
    private final IdempotencyRecordRepository idempotencyRecords;
    private final ObjectMapper objectMapper;
    private final AuditService audit;
    private final Clock clock = Clock.systemUTC();

    public OnboardingService(AppUserRepository users, FridgeRepository fridges, FridgeZoneRepository zones,
                             DeviceRepository devices, SensorProfileRepository profiles, SensorSlotRepository sensors,
                             IdempotencyRecordRepository idempotencyRecords,
                             ObjectMapper objectMapper, AuditService audit) {
        this.users = users; this.fridges = fridges; this.zones = zones; this.devices = devices; this.profiles = profiles; this.sensors = sensors;
        this.idempotencyRecords = idempotencyRecords; this.objectMapper = objectMapper; this.audit = audit;
    }

    @Transactional(readOnly = true)
    public OnboardingContracts.Status status(UUID userId) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "User is unavailable"));
        return new OnboardingContracts.Status(!user.onboardingRequired(), 3, 6, 4, defaultViews(), firstFridge(userId));
    }

    @Transactional
    public OnboardingContracts.FridgeSummary initialize(UUID userId, String idempotencyKey,
                                                         OnboardingContracts.InitializeRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Idempotency-Key is required");
        }
        String requestHash = requestHash(request);
        AppUser user = users.lockActiveById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "User is unavailable"));
        IdempotencyRecord previous = idempotencyRecords.findByUserIdAndIdempotencyKey(userId, idempotencyKey).orElse(null);
        if (previous != null) {
            if (!previous.getRequestHash().equals(requestHash)) {
                throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "Idempotency key was used for a different request");
            }
            return readStoredResponse(previous.getResponseBody());
        }

        if (!user.onboardingRequired()) {
            throw new ApiException(HttpStatus.CONFLICT, "ONBOARDING_ALREADY_COMPLETED", "Onboarding has already been completed");
        }
        validateUniqueNames(request.zones());

        Fridge fridge = fridges.save(new Fridge(UuidV7.next(), userId, request.fridgeName().trim()));
        List<FridgeZone> savedZones = new ArrayList<>();
        List<SensorSlot> savedSensors = new ArrayList<>();
        for (OnboardingContracts.ZoneRequest zoneRequest : request.zones()) {
            FridgeZone zone = zones.save(new FridgeZone(UuidV7.next(), fridge.getId(), zoneRequest.kind(),
                    zoneRequest.name().trim(), ZoneDefaults.forKind(zoneRequest.kind())));
            savedZones.add(zone);
            addSensorSlots(savedSensors, zone.getId(), SensorMetric.TEMPERATURE, zoneRequest.temperatureSensorCount());
            addSensorSlots(savedSensors, zone.getId(), SensorMetric.HUMIDITY, zoneRequest.humiditySensorCount());
        }
        sensors.saveAll(savedSensors);
        provisionVirtualProbes(userId, savedZones, savedSensors);
        user.completeOnboarding(clock.instant());
        OnboardingContracts.FridgeSummary response = toSummary(fridge, savedZones, savedSensors);
        idempotencyRecords.save(new IdempotencyRecord(UuidV7.next(), userId, idempotencyKey, requestHash,
                writeResponse(response), clock.instant().plus(Duration.ofDays(7))));
        audit.record(userId, "ONBOARDING_COMPLETED");
        return response;
    }

    @Transactional(readOnly = true)
    public List<OnboardingContracts.FridgeSummary> listFridges(UUID userId) {
        List<Fridge> owned = fridges.findByUserIdAndDeletedAtIsNullOrderByCreatedAtAsc(userId);
        if (owned.isEmpty()) return List.of();
        List<FridgeZone> ownedZones = zones.findByFridgeIdInAndDeletedAtIsNullOrderByCreatedAtAsc(
                owned.stream().map(Fridge::getId).toList());
        List<SensorSlot> ownedSensors = ownedZones.isEmpty() ? List.of() : sensors.findByZoneIdIn(ownedZones.stream().map(FridgeZone::getId).toList());
        Map<UUID, List<FridgeZone>> zonesByFridge = ownedZones.stream().collect(Collectors.groupingBy(FridgeZone::getFridgeId));
        return owned.stream().map(fridge -> toSummary(fridge, zonesByFridge.getOrDefault(fridge.getId(), List.of()), ownedSensors)).toList();
    }

    private OnboardingContracts.FridgeSummary firstFridge(UUID userId) {
        List<OnboardingContracts.FridgeSummary> owned = listFridges(userId);
        return owned.isEmpty() ? null : owned.getFirst();
    }

    private static void validateUniqueNames(List<OnboardingContracts.ZoneRequest> requests) {
        Set<String> names = new HashSet<>();
        for (OnboardingContracts.ZoneRequest request : requests) {
            String normalized = request.name().trim().toLowerCase(Locale.ROOT);
            if (!names.add(normalized)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Zone names must be unique",
                        Map.of("zones", "分区名称不能重复"));
            }
        }
    }

    private static void addSensorSlots(List<SensorSlot> output, UUID zoneId, SensorMetric metric, int count) {
        for (int slot = 1; slot <= count; slot++) output.add(new SensorSlot(UuidV7.next(), zoneId, metric, slot));
    }

    private void provisionVirtualProbes(UUID userId, List<FridgeZone> fridgeZones, List<SensorSlot> slots) {
        Map<UUID, FridgeZone> zonesById = fridgeZones.stream().collect(Collectors.toMap(FridgeZone::getId, zone -> zone));
        List<Device> virtualProbes = new ArrayList<>();
        for (SensorSlot slot : slots) {
            FridgeZone zone = zonesById.get(slot.getZoneId());
            if (zone == null) throw new IllegalStateException("Sensor zone is missing");
            var profile = profiles.findFirstByZoneKindAndMetricOrderByProfileVersionDesc(zone.getKind(), slot.getMetric())
                    .orElseThrow(() -> new IllegalStateException("Sensor profile is missing"));
            Device device = new Device(UuidV7.next(), userId, zone.getId(),
                    zone.getName() + (slot.getMetric() == SensorMetric.TEMPERATURE ? "温度" : "湿度") + "模拟探头",
                    com.xianzhi.fridge.fridge.domain.DeviceType.VIRTUAL);
            virtualProbes.add(device);
            slot.bind(device.getId(), profile.getId(), device.getName(),
                    slot.getMetric().name().toLowerCase(Locale.ROOT) + "-" + slot.getSlotIndex());
        }
        devices.saveAll(virtualProbes);
        sensors.saveAll(slots);
    }

    private OnboardingContracts.FridgeSummary toSummary(Fridge fridge, List<FridgeZone> fridgeZones, List<SensorSlot> allSensors) {
        Map<UUID, EnumMap<SensorMetric, Integer>> counts = new HashMap<>();
        for (SensorSlot sensor : allSensors) {
            counts.computeIfAbsent(sensor.getZoneId(), ignored -> new EnumMap<>(SensorMetric.class))
                    .merge(sensor.getMetric(), 1, Integer::sum);
        }
        List<OnboardingContracts.ZoneSummary> zoneSummaries = fridgeZones.stream().map(zone -> {
            Map<SensorMetric, Integer> zoneCounts = counts.getOrDefault(zone.getId(), new EnumMap<>(SensorMetric.class));
            int temperature = zoneCounts.getOrDefault(SensorMetric.TEMPERATURE, 0);
            int humidity = zoneCounts.getOrDefault(SensorMetric.HUMIDITY, 0);
            String bindingStatus = temperature + humidity == 0 ? "NOT_CONNECTED" : allBound(zone.getId(), allSensors) ? "BOUND" : "PENDING_BIND";
            return new OnboardingContracts.ZoneSummary(zone.getId(), zone.getKind(), zone.getName(), zone.isEnabled(),
                    zone.getTargetTemperatureC(), zone.getTargetHumidityPct(), zone.getSafeTemperatureMinC(),
                    zone.getSafeTemperatureMaxC(), zone.getSafeHumidityMinPct(), zone.getSafeHumidityMaxPct(),
                    temperature, humidity, bindingStatus);
        }).toList();
        return new OnboardingContracts.FridgeSummary(fridge.getId(), fridge.getName(), zoneSummaries);
    }

    private boolean allBound(UUID zoneId, List<SensorSlot> allSensors) {
        return allSensors.stream().filter(sensor -> sensor.getZoneId().equals(zoneId)).allMatch(sensor -> "BOUND".equals(sensor.getBindingStatus()));
    }

    private static List<OnboardingContracts.ZoneDefault> defaultViews() {
        return List.of(ZoneKind.CHILL, ZoneKind.FRESH, ZoneKind.VARIABLE, ZoneKind.FREEZE).stream().map(kind -> {
            ZoneDefaults value = ZoneDefaults.forKind(kind);
            return new OnboardingContracts.ZoneDefault(kind, value.suggestedName(), value.targetTemperatureC(),
                    value.targetHumidityPct(), value.safeTemperatureMinC(), value.safeTemperatureMaxC(),
                    value.safeHumidityMinPct(), value.safeHumidityMaxPct());
        }).toList();
    }

    private String requestHash(OnboardingContracts.InitializeRequest request) {
        try { return Hashing.sha256(objectMapper.writeValueAsString(request)); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Could not hash onboarding request", exception); }
    }

    private String writeResponse(OnboardingContracts.FridgeSummary response) {
        try { return objectMapper.writeValueAsString(response); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Could not store onboarding response", exception); }
    }

    private OnboardingContracts.FridgeSummary readStoredResponse(String response) {
        try { return objectMapper.readValue(response, OnboardingContracts.FridgeSummary.class); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Could not read onboarding response", exception); }
    }
}
