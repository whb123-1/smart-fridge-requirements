package com.xianzhi.fridge.fridge.api;

import com.xianzhi.fridge.fridge.application.DeviceService;
import com.xianzhi.fridge.shared.security.UserPrincipal;
import com.xianzhi.fridge.shared.web.ApiEnvelope;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    @DeleteMapping("/devices/{deviceId}/sensors/{sensorId}")
    public ResponseEntity<ApiEnvelope<Void>> unbind(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID deviceId, @PathVariable UUID sensorId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        devices.unbind(principal.userId(), deviceId, sensorId, key); return ResponseEntity.ok(ApiEnvelope.ok(null));
    }
}
