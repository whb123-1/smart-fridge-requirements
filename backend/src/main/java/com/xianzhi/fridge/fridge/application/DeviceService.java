package com.xianzhi.fridge.fridge.application;

import com.xianzhi.fridge.fridge.api.DeviceContracts;
import com.xianzhi.fridge.fridge.infrastructure.Device;
import com.xianzhi.fridge.fridge.infrastructure.DeviceRepository;
import com.xianzhi.fridge.fridge.infrastructure.Fridge;
import com.xianzhi.fridge.fridge.infrastructure.FridgeRepository;
import com.xianzhi.fridge.fridge.infrastructure.FridgeZone;
import com.xianzhi.fridge.fridge.infrastructure.FridgeZoneRepository;
import com.xianzhi.fridge.fridge.infrastructure.SensorSlot;
import com.xianzhi.fridge.fridge.infrastructure.SensorSlotRepository;
import com.xianzhi.fridge.shared.application.AuditService;
import com.xianzhi.fridge.shared.application.IdempotencyService;
import com.xianzhi.fridge.shared.web.ApiException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceService {
    private final DeviceRepository devices;
    private final SensorSlotRepository sensors;
    private final FridgeZoneRepository zones;
    private final FridgeRepository fridges;
    private final IdempotencyService idempotency;
    private final AuditService audit;
    private final Clock clock;

    public DeviceService(DeviceRepository devices, SensorSlotRepository sensors,
                         FridgeZoneRepository zones, FridgeRepository fridges,
                         IdempotencyService idempotency, AuditService audit, Clock clock) {
        this.devices = devices; this.sensors = sensors; this.zones = zones; this.fridges = fridges;
        this.idempotency = idempotency; this.audit = audit; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<DeviceContracts.DeviceView> list(UUID userId, UUID zoneId) {
        ownedZone(userId, zoneId);
        return devices.findByUserIdAndZoneIdAndDeletedAtIsNullOrderByCreatedAtAsc(userId, zoneId).stream()
                .map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public List<DeviceContracts.SensorView> zoneSensors(UUID userId, UUID zoneId) {
        ownedZone(userId, zoneId);
        return sensors.findByZoneIdAndEnabledTrueOrderBySlotIndexAsc(zoneId).stream().map(this::sensorView).toList();
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public void unbind(UUID userId, UUID deviceId, UUID sensorId, String key) {
        String path = "/api/v1/devices/" + deviceId + "/sensors/" + sensorId;
        Map<String, Boolean> replay = idempotency.replay(userId, key, "DELETE", path, sensorId, Map.class);
        if (replay != null) return;
        Device device = ownedDevice(userId, deviceId);
        SensorSlot slot = sensors.findByIdAndDeviceId(sensorId, deviceId)
                .orElseThrow(() -> notFound("SENSOR_NOT_FOUND", "Sensor not found"));
        slot.unbind(); sensors.save(slot);
        device.disable(clock.instant()); devices.save(device);
        idempotency.save(userId, key, "DELETE", path, sensorId, Map.of("deleted", true), 200);
        audit.record(userId, "SENSOR_UNBOUND");
    }

    private DeviceContracts.DeviceView view(Device device) {
        List<DeviceContracts.SensorView> bound = sensors.findByDeviceIdAndEnabledTrueOrderBySlotIndexAsc(device.getId()).stream()
                .map(this::sensorView).toList();
        return new DeviceContracts.DeviceView(device.getId(), device.getZoneId(), device.getName(), device.getType(),
                device.getLastSeenAt(), device.getFirmwareVersion(), bound);
    }
    private DeviceContracts.SensorView sensorView(SensorSlot sensor) {
        return new DeviceContracts.SensorView(sensor.getId(), sensor.getZoneId(), sensor.getDeviceId(), sensor.getMetric(), sensor.getName(),
                sensor.getExternalKey(), sensor.getSlotIndex(), sensor.getBindingStatus(), sensor.isEnabled(), sensor.getLastValue(),
                sensor.getLastUnit(), sensor.getLastQuality(), sensor.getLastObservedAt(), sensor.getLastReceivedAt());
    }
    private FridgeZone ownedZone(UUID userId, UUID zoneId) {
        FridgeZone zone = zones.findById(zoneId).filter(value -> value.getDeletedAt() == null)
                .orElseThrow(() -> notFound("ZONE_NOT_FOUND", "Zone not found"));
        Fridge fridge = fridges.findById(zone.getFridgeId()).filter(value -> userId.equals(value.getUserId()) && value.getDeletedAt() == null)
                .orElseThrow(() -> notFound("ZONE_NOT_FOUND", "Zone not found"));
        return zone;
    }
    private Device ownedDevice(UUID userId, UUID id) { return devices.findByIdAndUserIdAndDeletedAtIsNull(id, userId).orElseThrow(() -> notFound("DEVICE_NOT_FOUND", "Device not found")); }
    private static ApiException notFound(String code, String message) { return new ApiException(HttpStatus.NOT_FOUND, code, message); }
}
