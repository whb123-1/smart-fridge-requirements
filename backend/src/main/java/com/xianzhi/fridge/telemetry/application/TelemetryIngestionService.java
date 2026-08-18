package com.xianzhi.fridge.telemetry.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianzhi.fridge.fridge.domain.DeviceStatus;
import com.xianzhi.fridge.fridge.domain.DeviceType;
import com.xianzhi.fridge.fridge.domain.SensorMetric;
import com.xianzhi.fridge.fridge.infrastructure.Device;
import com.xianzhi.fridge.fridge.infrastructure.DeviceRepository;
import com.xianzhi.fridge.fridge.infrastructure.FridgeZone;
import com.xianzhi.fridge.fridge.infrastructure.FridgeZoneRepository;
import com.xianzhi.fridge.fridge.infrastructure.SensorProfile;
import com.xianzhi.fridge.fridge.infrastructure.SensorProfileRepository;
import com.xianzhi.fridge.fridge.infrastructure.SensorSlot;
import com.xianzhi.fridge.fridge.infrastructure.SensorSlotRepository;
import com.xianzhi.fridge.shared.application.AuditService;
import com.xianzhi.fridge.shared.domain.Hashing;
import com.xianzhi.fridge.shared.domain.UuidV7;
import com.xianzhi.fridge.shared.infrastructure.OutboxEvent;
import com.xianzhi.fridge.shared.infrastructure.OutboxEventRepository;
import com.xianzhi.fridge.telemetry.api.TelemetryContracts;
import com.xianzhi.fridge.telemetry.domain.ReadingQuality;
import com.xianzhi.fridge.telemetry.domain.ReadingSource;
import com.xianzhi.fridge.telemetry.domain.TelemetryResult;
import com.xianzhi.fridge.telemetry.infrastructure.SensorReadingStore;
import com.xianzhi.fridge.telemetry.infrastructure.TelemetryMessage;
import com.xianzhi.fridge.telemetry.infrastructure.TelemetryMessageRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TelemetryIngestionService {
    private final DeviceRepository devices;
    private final SensorSlotRepository sensors;
    private final SensorProfileRepository profiles;
    private final FridgeZoneRepository zones;
    private final TelemetryMessageRepository messages;
    private final SensorReadingStore readings;
    private final OutboxEventRepository outbox;
    private final ObjectMapper mapper;
    private final AuditService audit;
    private final Clock clock;

    public TelemetryIngestionService(DeviceRepository devices, SensorSlotRepository sensors, SensorProfileRepository profiles,
                                     FridgeZoneRepository zones, TelemetryMessageRepository messages, SensorReadingStore readings,
                                     OutboxEventRepository outbox, ObjectMapper mapper, AuditService audit, Clock clock) {
        this.devices = devices; this.sensors = sensors; this.profiles = profiles; this.zones = zones; this.messages = messages;
        this.readings = readings; this.outbox = outbox; this.mapper = mapper; this.audit = audit; this.clock = clock;
    }

    @Transactional
    public TelemetryResult ingest(UUID topicDeviceId, String payload) {
        Device device = devices.findById(topicDeviceId).orElse(null);
        if (device == null || device.getDeletedAt() != null || device.getStatus() != DeviceStatus.ACTIVE) return TelemetryResult.REJECTED;
        Instant receivedAt = clock.instant();
        TelemetryContracts.Message message;
        try { message = mapper.readValue(payload, TelemetryContracts.Message.class); }
        catch (JsonProcessingException exception) { audit.record(device.getUserId(), "TELEMETRY_SCHEMA_REJECTED"); return TelemetryResult.REJECTED; }
        if (message.messageId() == null || message.observedAt() == null) return reject(device, message, payload, receivedAt, "TELEMETRY_SCHEMA_INVALID");
        if (messages.existsByDeviceIdAndMessageId(device.getId(), message.messageId().toString())) return TelemetryResult.DUPLICATE;
        try {
            validateTime(message.observedAt(), receivedAt);
            List<SensorSlot> bound = sensors.findByDeviceIdAndEnabledTrueOrderBySlotIndexAsc(device.getId());
            Map<UUID, SensorSlot> byId = new HashMap<>(); bound.forEach(sensor -> byId.put(sensor.getId(), sensor));
            if (message.readings() == null || message.readings().isEmpty()) throw rejected("TELEMETRY_SCHEMA_INVALID", "Readings are required");
            if (message.readings().stream().map(TelemetryContracts.Reading::sensorId).distinct().count() != message.readings().size()) {
                throw rejected("TELEMETRY_DUPLICATE_SENSOR", "A sensor may appear only once per message");
            }
            FridgeZone zone = zones.findById(device.getZoneId()).orElseThrow();
            recordAccepted(device, message, payload, receivedAt, byId, zone);
            return TelemetryResult.ACCEPTED;
        } catch (TelemetryRejectedException exception) {
            return reject(device, message, payload, receivedAt, exception.code());
        }
    }

    private void recordAccepted(Device device, TelemetryContracts.Message message, String payload, Instant receivedAt,
                                Map<UUID, SensorSlot> byId, FridgeZone zone) {
        List<PreparedReading> prepared = new ArrayList<>();
        for (TelemetryContracts.Reading incoming : message.readings()) {
            SensorSlot sensor = byId.get(incoming.sensorId());
            if (sensor == null || sensor.getMetric() != incoming.metric()) throw rejected("TELEMETRY_SENSOR_OWNERSHIP", "Sensor does not belong to device");
            BigDecimal value = normalize(incoming);
            SensorProfile profile = profiles.findById(sensor.getProfileId()).orElseThrow(() -> rejected("SENSOR_PROFILE_MISSING", "Sensor profile is missing"));
            if (value.compareTo(profile.getPhysicalMin()) < 0 || value.compareTo(profile.getPhysicalMax()) > 0) {
                throw rejected("TELEMETRY_PHYSICAL_RANGE", "Reading is outside physical range");
            }
            if (sensor.getLastObservedAt() != null && message.observedAt().isBefore(sensor.getLastObservedAt().minus(Duration.ofHours(48)))) {
                throw rejected("TELEMETRY_TOO_OLD", "Reading is older than the accepted history window");
            }
            boolean newer = sensor.getLastObservedAt() == null || message.observedAt().isAfter(sensor.getLastObservedAt());
            ReadingQuality quality = incoming.quality() == ReadingQuality.BAD ? ReadingQuality.BAD
                    : newer && suspect(sensor, profile, value, message.observedAt()) ? ReadingQuality.SUSPECT : ReadingQuality.GOOD;
            prepared.add(new PreparedReading(sensor, value, quality, newer));
        }
        TelemetryMessage stored = new TelemetryMessage(UuidV7.next(), device.getId(), message.messageId().toString(), message.observedAt(), receivedAt,
                message.firmwareVersion(), source(device), TelemetryResult.ACCEPTED, null, Hashing.sha256(payload), payload);
        stored = messages.save(stored);
        for (PreparedReading reading : prepared) {
            SensorSlot sensor = reading.sensor();
            if (reading.quality() == ReadingQuality.BAD) {
                audit.record(device.getUserId(), "TELEMETRY_BAD_READING");
                continue;
            }
            readings.insert(UuidV7.next(), device.getUserId(), zone.getFridgeId(), zone.getId(), device.getId(), sensor.getId(), stored.getId(),
                    sensor.getMetric(), reading.value(), normalizedUnit(sensor.getMetric()), reading.quality(), source(device), message.observedAt(), receivedAt);
            if (reading.newer() && reading.quality() == ReadingQuality.GOOD) {
                boolean recovered = sensor.getConsecutiveSuspectCount() >= 3;
                sensor.accept(reading.value(), normalizedUnit(sensor.getMetric()), message.observedAt(), receivedAt);
                if (recovered) publish(sensor.getId(), "SensorRecovered", Map.of("sensorId", sensor.getId(), "zoneId", zone.getId()));
            } else if (reading.newer() && reading.quality() == ReadingQuality.SUSPECT && sensor.suspect() == 3) {
                publish(sensor.getId(), "SensorSuspectDetected", Map.of("sensorId", sensor.getId(), "zoneId", zone.getId()));
            }
        }
        device.seen(receivedAt, message.firmwareVersion());
        publish(zone.getId(), "SensorReadingAccepted", Map.of("zoneId", zone.getId(), "observedAt", message.observedAt()));
    }

    private TelemetryResult reject(Device device, TelemetryContracts.Message message, String payload, Instant receivedAt, String code) {
        if (message != null && message.messageId() != null && message.observedAt() != null
                && !messages.existsByDeviceIdAndMessageId(device.getId(), message.messageId().toString())) {
            recordMessage(device, message, payload, receivedAt, TelemetryResult.REJECTED, code);
        }
        audit.record(device.getUserId(), code);
        return TelemetryResult.REJECTED;
    }

    private void recordMessage(Device device, TelemetryContracts.Message message, String payload, Instant receivedAt,
                               TelemetryResult result, String rejectionCode) {
        messages.save(new TelemetryMessage(UuidV7.next(), device.getId(), message.messageId().toString(), message.observedAt(), receivedAt,
                message.firmwareVersion(), source(device), result, rejectionCode, Hashing.sha256(payload), payload));
    }
    private void validateTime(Instant observedAt, Instant receivedAt) {
        if (observedAt.isAfter(receivedAt.plus(Duration.ofMinutes(10)))) throw rejected("TELEMETRY_FUTURE", "Observed time is too far in the future");
    }
    private BigDecimal normalize(TelemetryContracts.Reading reading) {
        if (reading.metric() == null || reading.value() == null || reading.unit() == null || reading.quality() == null) {
            throw rejected("TELEMETRY_SCHEMA_INVALID", "Reading fields are required");
        }
        String unit = reading.unit().toUpperCase();
        if (reading.metric() == SensorMetric.HUMIDITY) {
            if (!"PERCENT".equals(unit)) throw rejected("TELEMETRY_UNIT_INVALID", "Humidity must use PERCENT");
            return reading.value();
        }
        if ("C".equals(unit)) return reading.value();
        if ("F".equals(unit)) return reading.value().subtract(BigDecimal.valueOf(32)).multiply(BigDecimal.valueOf(5))
                .divide(BigDecimal.valueOf(9), 3, RoundingMode.HALF_UP);
        throw rejected("TELEMETRY_UNIT_INVALID", "Temperature must use C or F");
    }
    private boolean suspect(SensorSlot sensor, SensorProfile profile, BigDecimal value, Instant observedAt) {
        if (sensor.getLastValue() == null || sensor.getLastObservedAt() == null || !observedAt.isAfter(sensor.getLastObservedAt())) return false;
        BigDecimal minutes = BigDecimal.valueOf(Duration.between(sensor.getLastObservedAt(), observedAt).toMillis())
                .divide(BigDecimal.valueOf(60000), 6, RoundingMode.HALF_UP);
        if (minutes.signum() <= 0) return false;
        BigDecimal rate = value.subtract(sensor.getLastValue()).abs().divide(minutes, 3, RoundingMode.HALF_UP);
        return rate.compareTo(profile.getMaxChangePerMinute()) > 0;
    }
    private String normalizedUnit(SensorMetric metric) { return metric == SensorMetric.TEMPERATURE ? "C" : "PERCENT"; }
    private ReadingSource source(Device device) { return device.getType() == DeviceType.VIRTUAL ? ReadingSource.EXTERNAL_DEBUG : ReadingSource.DEVICE; }
    private void publish(UUID aggregateId, String eventType, Map<String, Object> payload) {
        try { outbox.save(new OutboxEvent(UuidV7.next(), "Telemetry", aggregateId, eventType, mapper.writeValueAsString(payload))); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Could not serialize telemetry event", exception); }
    }
    private static TelemetryRejectedException rejected(String code, String message) { return new TelemetryRejectedException(code, message); }
    private record PreparedReading(SensorSlot sensor, BigDecimal value, ReadingQuality quality, boolean newer) { }
}
