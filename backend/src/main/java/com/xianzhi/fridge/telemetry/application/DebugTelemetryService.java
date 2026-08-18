package com.xianzhi.fridge.telemetry.application;

import com.xianzhi.fridge.fridge.domain.DeviceType;
import com.xianzhi.fridge.fridge.infrastructure.Device;
import com.xianzhi.fridge.fridge.infrastructure.DeviceRepository;
import com.xianzhi.fridge.fridge.infrastructure.SensorSlot;
import com.xianzhi.fridge.fridge.infrastructure.SensorSlotRepository;
import com.xianzhi.fridge.identity.infrastructure.AppUser;
import com.xianzhi.fridge.identity.infrastructure.AppUserRepository;
import com.xianzhi.fridge.shared.application.IdempotencyService;
import com.xianzhi.fridge.shared.domain.UuidV7;
import com.xianzhi.fridge.shared.web.ApiException;
import com.xianzhi.fridge.telemetry.api.DebugTelemetryContracts;
import com.xianzhi.fridge.telemetry.config.TelemetryProperties;
import com.xianzhi.fridge.telemetry.infrastructure.DebugTelemetryScenario;
import com.xianzhi.fridge.telemetry.infrastructure.DebugTelemetryScenarioRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DebugTelemetryService {
    private final DebugTelemetryScenarioRepository scenarios;
    private final DeviceRepository devices;
    private final SensorSlotRepository sensors;
    private final AppUserRepository users;
    private final TelemetryProperties properties;
    private final IdempotencyService idempotency;
    private final Clock clock;
    public DebugTelemetryService(DebugTelemetryScenarioRepository scenarios, DeviceRepository devices,
                                 SensorSlotRepository sensors, AppUserRepository users, TelemetryProperties properties,
                                 IdempotencyService idempotency, Clock clock) {
        this.scenarios = scenarios; this.devices = devices; this.sensors = sensors; this.users = users;
        this.properties = properties; this.idempotency = idempotency; this.clock = clock;
    }
    @Transactional(readOnly = true)
    public List<DebugTelemetryContracts.View> list(UUID userId) { operator(userId); return scenarios.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::view).toList(); }
    @Transactional
    public DebugTelemetryContracts.View create(UUID userId, String key, DebugTelemetryContracts.CreateRequest request) {
        operator(userId); String path = "/api/v1/debug/telemetry/scenarios";
        DebugTelemetryContracts.View replay = idempotency.replay(userId, key, "POST", path, request, DebugTelemetryContracts.View.class);
        if (replay != null) return replay;
        ownedVirtualSensor(userId, request.deviceId(), request.sensorId());
        if ("TARGET".equals(request.mode()) && request.targetValue() == null) throw validation("targetValue is required for TARGET mode");
        DebugTelemetryScenario scenario = scenarios.save(new DebugTelemetryScenario(UuidV7.next(), userId, request.deviceId(), request.sensorId(),
                request.mode(), request.targetValue(), request.durationMinutes(), request.jitter() == null ? BigDecimal.ZERO : request.jitter(), clock.instant()));
        DebugTelemetryContracts.View response = view(scenario); idempotency.save(userId, key, "POST", path, request, response, 200); return response;
    }
    @Transactional
    public DebugTelemetryContracts.View update(UUID userId, UUID id, String key, DebugTelemetryContracts.UpdateRequest request) {
        operator(userId); String path = "/api/v1/debug/telemetry/scenarios/" + id;
        DebugTelemetryContracts.View replay = idempotency.replay(userId, key, "PATCH", path, request, DebugTelemetryContracts.View.class);
        if (replay != null) return replay;
        DebugTelemetryScenario scenario = owned(userId, id);
        if (Boolean.FALSE.equals(request.active())) scenario.stop(clock.instant());
        else scenario.update(request.mode(), request.targetValue(), request.durationMinutes(), request.jitter(), clock.instant());
        DebugTelemetryContracts.View response = view(scenarios.save(scenario)); idempotency.save(userId, key, "PATCH", path, request, response, 200); return response;
    }
    @Transactional
    @SuppressWarnings("unchecked")
    public void delete(UUID userId, UUID id, String key) {
        operator(userId); String path = "/api/v1/debug/telemetry/scenarios/" + id;
        Map<String, Boolean> replay = idempotency.replay(userId, key, "DELETE", path, id, Map.class); if (replay != null) return;
        owned(userId, id).stop(clock.instant()); idempotency.save(userId, key, "DELETE", path, id, Map.of("deleted", true), 200);
    }
    private void operator(UUID userId) {
        AppUser user = users.findById(userId).orElseThrow();
        if (!properties.debugOperators().contains(user.getUsername().toLowerCase())) throw new ApiException(HttpStatus.FORBIDDEN, "DEBUG_OPERATOR_REQUIRED", "Debug telemetry access is not enabled for this user");
    }
    private SensorSlot ownedVirtualSensor(UUID userId, UUID deviceId, UUID sensorId) {
        Device device = devices.findByIdAndUserIdAndDeletedAtIsNull(deviceId, userId).filter(value -> value.getType() == DeviceType.VIRTUAL)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "VIRTUAL_DEVICE_NOT_FOUND", "Virtual device not found"));
        return sensors.findByIdAndDeviceId(sensorId, device.getId()).filter(value -> "BOUND".equals(value.getBindingStatus()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SENSOR_NOT_FOUND", "Bound sensor not found"));
    }
    private DebugTelemetryScenario owned(UUID userId, UUID id) { return scenarios.findByIdAndUserId(id, userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DEBUG_SCENARIO_NOT_FOUND", "Debug scenario not found")); }
    private DebugTelemetryContracts.View view(DebugTelemetryScenario value) { return new DebugTelemetryContracts.View(value.getId(), value.getDeviceId(), value.getSensorId(), value.getMode(), value.getTargetValue(), value.getDurationMinutes(), value.getJitter(), value.getStatus(), value.getStartedAt(), value.getEndsAt(), value.getNextEmitAt(), value.getLastEmitAt(), value.getCreatedAt(), value.getUpdatedAt()); }
    private static ApiException validation(String message) { return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message); }
}
