package com.xianzhi.fridge.fridge.application;

import com.xianzhi.fridge.fridge.api.DeviceContracts;
import com.xianzhi.fridge.fridge.domain.DeviceStatus;
import com.xianzhi.fridge.fridge.infrastructure.Device;
import com.xianzhi.fridge.fridge.infrastructure.DeviceRepository;
import com.xianzhi.fridge.fridge.infrastructure.Fridge;
import com.xianzhi.fridge.fridge.infrastructure.FridgeRepository;
import com.xianzhi.fridge.fridge.infrastructure.FridgeZone;
import com.xianzhi.fridge.fridge.infrastructure.FridgeZoneRepository;
import com.xianzhi.fridge.fridge.infrastructure.SensorProfile;
import com.xianzhi.fridge.fridge.infrastructure.SensorProfileRepository;
import com.xianzhi.fridge.fridge.infrastructure.SensorSlot;
import com.xianzhi.fridge.fridge.infrastructure.SensorSlotRepository;
import com.xianzhi.fridge.shared.application.AuditService;
import com.xianzhi.fridge.shared.application.IdempotencyService;
import com.xianzhi.fridge.shared.domain.UuidV7;
import com.xianzhi.fridge.shared.web.ApiException;
import com.xianzhi.fridge.telemetry.config.TelemetryProperties;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceService {
    private final DeviceRepository devices;
    private final SensorSlotRepository sensors;
    private final SensorProfileRepository profiles;
    private final FridgeZoneRepository zones;
    private final FridgeRepository fridges;
    private final PasswordEncoder passwords;
    private final IdempotencyService idempotency;
    private final TelemetryProperties telemetry;
    private final AuditService audit;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public DeviceService(DeviceRepository devices, SensorSlotRepository sensors, SensorProfileRepository profiles,
                         FridgeZoneRepository zones, FridgeRepository fridges, PasswordEncoder passwords,
                         IdempotencyService idempotency, TelemetryProperties telemetry, AuditService audit, Clock clock) {
        this.devices = devices; this.sensors = sensors; this.profiles = profiles; this.zones = zones; this.fridges = fridges;
        this.passwords = passwords; this.idempotency = idempotency; this.telemetry = telemetry; this.audit = audit; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<DeviceContracts.DeviceView> list(UUID userId, UUID zoneId) {
        ownedZone(userId, zoneId);
        return devices.findByUserIdAndZoneIdAndDeletedAtIsNullOrderByCreatedAtAsc(userId, zoneId).stream()
                .map(device -> view(device, null)).toList();
    }

    @Transactional(readOnly = true)
    public List<DeviceContracts.SensorView> sensors(UUID userId, UUID deviceId) {
        ownedDevice(userId, deviceId);
        return sensors.findByDeviceIdAndEnabledTrueOrderBySlotIndexAsc(deviceId).stream().map(this::sensorView).toList();
    }

    @Transactional(readOnly = true)
    public List<DeviceContracts.SensorView> zoneSensors(UUID userId, UUID zoneId) {
        ownedZone(userId, zoneId);
        return sensors.findByZoneIdAndEnabledTrueOrderBySlotIndexAsc(zoneId).stream().map(this::sensorView).toList();
    }

    @Transactional
    public DeviceContracts.DeviceView create(UUID userId, UUID zoneId, String key, DeviceContracts.CreateDeviceRequest request) {
        String path = "/api/v1/zones/" + zoneId + "/devices";
        DeviceContracts.DeviceView replay = idempotency.replay(userId, key, "POST", path, request, DeviceContracts.DeviceView.class);
        if (replay != null) return replay;
        ownedZone(userId, zoneId);
        UUID id = UuidV7.next();
        String secret = secret();
        Device device = devices.save(new Device(id, userId, zoneId, request.name().trim(), request.type(),
                "xianzhi-" + id, id.toString(), passwords.encode(secret)));
        DeviceContracts.MqttCredential credential = credential(device, secret);
        DeviceContracts.DeviceView response = view(device, credential);
        idempotency.save(userId, key, "POST", path, request, response, 200);
        audit.record(userId, "DEVICE_REGISTERED");
        return response;
    }

    @Transactional
    public DeviceContracts.DeviceView update(UUID userId, UUID deviceId, String key, DeviceContracts.UpdateDeviceRequest request) {
        String path = "/api/v1/devices/" + deviceId;
        DeviceContracts.DeviceView replay = idempotency.replay(userId, key, "PATCH", path, request, DeviceContracts.DeviceView.class);
        if (replay != null) return replay;
        Device device = ownedDevice(userId, deviceId);
        device.update(request.name(), request.status());
        DeviceContracts.DeviceView response = view(devices.save(device), null);
        idempotency.save(userId, key, "PATCH", path, request, response, 200);
        audit.record(userId, "DEVICE_UPDATED");
        return response;
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public void delete(UUID userId, UUID deviceId, String key) {
        String path = "/api/v1/devices/" + deviceId;
        Map<String, Boolean> replay = idempotency.replay(userId, key, "DELETE", path, deviceId, Map.class);
        if (replay != null) return;
        Device device = ownedDevice(userId, deviceId);
        sensors.findByDeviceIdAndEnabledTrueOrderBySlotIndexAsc(deviceId).forEach(SensorSlot::unbind);
        device.disable(clock.instant());
        devices.save(device);
        idempotency.save(userId, key, "DELETE", path, deviceId, Map.of("deleted", true), 200);
        audit.record(userId, "DEVICE_DISABLED");
    }

    @Transactional
    public DeviceContracts.DeviceView rotate(UUID userId, UUID deviceId, String key) {
        String path = "/api/v1/devices/" + deviceId + "/credentials/rotate";
        DeviceContracts.DeviceView replay = idempotency.replay(userId, key, "POST", path, deviceId, DeviceContracts.DeviceView.class);
        if (replay != null) return replay;
        Device device = ownedDevice(userId, deviceId);
        if (device.getStatus() != DeviceStatus.ACTIVE) throw conflict("DEVICE_DISABLED", "Device is disabled");
        String secret = secret(); device.rotateCredential(passwords.encode(secret));
        DeviceContracts.DeviceView response = view(devices.save(device), credential(device, secret));
        idempotency.save(userId, key, "POST", path, deviceId, response, 200);
        audit.record(userId, "DEVICE_CREDENTIAL_ROTATED");
        return response;
    }

    @Transactional
    public DeviceContracts.SensorView bind(UUID userId, UUID deviceId, String key, DeviceContracts.BindSensorRequest request) {
        String path = "/api/v1/devices/" + deviceId + "/sensors";
        DeviceContracts.SensorView replay = idempotency.replay(userId, key, "POST", path, request, DeviceContracts.SensorView.class);
        if (replay != null) return replay;
        Device device = ownedDevice(userId, deviceId);
        SensorSlot slot = sensors.findById(request.slotId()).orElseThrow(() -> notFound("SENSOR_SLOT_NOT_FOUND", "Sensor slot not found"));
        if (!device.getZoneId().equals(slot.getZoneId())) throw notFound("SENSOR_SLOT_NOT_FOUND", "Sensor slot not found");
        if (!"PENDING_BIND".equals(slot.getBindingStatus())) throw conflict("SENSOR_SLOT_ALREADY_BOUND", "Sensor slot is already bound");
        FridgeZone zone = ownedZone(userId, slot.getZoneId());
        SensorProfile profile = profiles.findFirstByZoneKindAndMetricOrderByProfileVersionDesc(zone.getKind(), slot.getMetric())
                .orElseThrow(() -> new IllegalStateException("Sensor profile is missing"));
        slot.bind(deviceId, profile.getId(), request.name().trim(), request.externalKey().trim());
        DeviceContracts.SensorView response = sensorView(sensors.save(slot));
        idempotency.save(userId, key, "POST", path, request, response, 200);
        audit.record(userId, "SENSOR_BOUND");
        return response;
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public void unbind(UUID userId, UUID deviceId, UUID sensorId, String key) {
        String path = "/api/v1/devices/" + deviceId + "/sensors/" + sensorId;
        Map<String, Boolean> replay = idempotency.replay(userId, key, "DELETE", path, sensorId, Map.class);
        if (replay != null) return;
        ownedDevice(userId, deviceId);
        SensorSlot slot = sensors.findByIdAndDeviceId(sensorId, deviceId)
                .orElseThrow(() -> notFound("SENSOR_NOT_FOUND", "Sensor not found"));
        slot.unbind(); sensors.save(slot);
        idempotency.save(userId, key, "DELETE", path, sensorId, Map.of("deleted", true), 200);
        audit.record(userId, "SENSOR_UNBOUND");
    }

    private DeviceContracts.DeviceView view(Device device, DeviceContracts.MqttCredential credential) {
        List<DeviceContracts.SensorView> bound = sensors.findByDeviceIdAndEnabledTrueOrderBySlotIndexAsc(device.getId()).stream()
                .map(this::sensorView).toList();
        return new DeviceContracts.DeviceView(device.getId(), device.getZoneId(), device.getName(), device.getType(), device.getStatus(),
                device.getMqttClientId(), device.getLastSeenAt(), device.getFirmwareVersion(), bound, credential);
    }
    private DeviceContracts.SensorView sensorView(SensorSlot sensor) {
        return new DeviceContracts.SensorView(sensor.getId(), sensor.getZoneId(), sensor.getDeviceId(), sensor.getMetric(), sensor.getName(),
                sensor.getExternalKey(), sensor.getSlotIndex(), sensor.getBindingStatus(), sensor.isEnabled(), sensor.getLastValue(),
                sensor.getLastUnit(), sensor.getLastQuality(), sensor.getLastObservedAt(), sensor.getLastReceivedAt());
    }
    private DeviceContracts.MqttCredential credential(Device device, String secret) {
        return new DeviceContracts.MqttCredential(telemetry.getPublicBrokerUrl(), device.getMqttClientId(), device.getMqttUsername(), secret,
                "smart-fridge/v1/" + device.getId() + "/telemetry", 1, false);
    }
    private FridgeZone ownedZone(UUID userId, UUID zoneId) {
        FridgeZone zone = zones.findById(zoneId).filter(value -> value.getDeletedAt() == null)
                .orElseThrow(() -> notFound("ZONE_NOT_FOUND", "Zone not found"));
        Fridge fridge = fridges.findById(zone.getFridgeId()).filter(value -> userId.equals(value.getUserId()) && value.getDeletedAt() == null)
                .orElseThrow(() -> notFound("ZONE_NOT_FOUND", "Zone not found"));
        return zone;
    }
    private Device ownedDevice(UUID userId, UUID id) { return devices.findByIdAndUserIdAndDeletedAtIsNull(id, userId).orElseThrow(() -> notFound("DEVICE_NOT_FOUND", "Device not found")); }
    private String secret() { byte[] bytes = new byte[32]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static ApiException notFound(String code, String message) { return new ApiException(HttpStatus.NOT_FOUND, code, message); }
    private static ApiException conflict(String code, String message) { return new ApiException(HttpStatus.CONFLICT, code, message); }
}
