package com.xianzhi.fridge.fridge.api;

import com.xianzhi.fridge.fridge.application.DeviceService;
import com.xianzhi.fridge.shared.security.UserPrincipal;
import com.xianzhi.fridge.shared.web.ApiEnvelope;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class DeviceController {
    private final DeviceService devices;
    public DeviceController(DeviceService devices) { this.devices = devices; }
    @GetMapping("/zones/{id}/devices")
    public ApiEnvelope<List<DeviceContracts.DeviceView>> list(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ApiEnvelope.ok(devices.list(principal.userId(), id));
    }
    @GetMapping("/zones/{id}/sensors")
    public ApiEnvelope<List<DeviceContracts.SensorView>> zoneSensors(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ApiEnvelope.ok(devices.zoneSensors(principal.userId(), id));
    }
    @PostMapping("/zones/{id}/sensors/initialize")
    public ApiEnvelope<DeviceContracts.DeviceView> initializeSensor(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id, @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody DeviceContracts.InitializeSensorRequest request) {
        return ApiEnvelope.ok(devices.initializeSensor(principal.userId(), id, key, request));
    }
    @PostMapping("/zones/{id}/devices")
    public ApiEnvelope<DeviceContracts.DeviceView> create(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody DeviceContracts.CreateDeviceRequest request) {
        return ApiEnvelope.ok(devices.create(principal.userId(), id, key, request));
    }
    @PatchMapping("/devices/{id}")
    public ApiEnvelope<DeviceContracts.DeviceView> update(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody DeviceContracts.UpdateDeviceRequest request) {
        return ApiEnvelope.ok(devices.update(principal.userId(), id, key, request));
    }
    @DeleteMapping("/devices/{id}")
    public ResponseEntity<ApiEnvelope<Void>> delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        devices.delete(principal.userId(), id, key); return ResponseEntity.ok(ApiEnvelope.ok(null));
    }
    @PostMapping("/devices/{id}/credentials/rotate")
    public ApiEnvelope<DeviceContracts.DeviceView> rotate(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ApiEnvelope.ok(devices.rotate(principal.userId(), id, key));
    }
    @GetMapping("/devices/{id}/sensors")
    public ApiEnvelope<List<DeviceContracts.SensorView>> sensors(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ApiEnvelope.ok(devices.sensors(principal.userId(), id));
    }
    @PostMapping("/devices/{id}/sensors")
    public ApiEnvelope<DeviceContracts.SensorView> bind(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody DeviceContracts.BindSensorRequest request) {
        return ApiEnvelope.ok(devices.bind(principal.userId(), id, key, request));
    }
    @DeleteMapping("/devices/{deviceId}/sensors/{sensorId}")
    public ResponseEntity<ApiEnvelope<Void>> unbind(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID deviceId, @PathVariable UUID sensorId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        devices.unbind(principal.userId(), deviceId, sensorId, key); return ResponseEntity.ok(ApiEnvelope.ok(null));
    }
}
