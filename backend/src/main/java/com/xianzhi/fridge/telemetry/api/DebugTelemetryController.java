package com.xianzhi.fridge.telemetry.api;

import com.xianzhi.fridge.shared.security.UserPrincipal;
import com.xianzhi.fridge.shared.web.ApiEnvelope;
import com.xianzhi.fridge.telemetry.application.DebugTelemetryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
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
@Profile({"dev", "test"})
@RequestMapping("/api/v1/debug/telemetry/scenarios")
public class DebugTelemetryController {
    private final DebugTelemetryService scenarios;
    public DebugTelemetryController(DebugTelemetryService scenarios) { this.scenarios = scenarios; }
    @GetMapping public ApiEnvelope<List<DebugTelemetryContracts.View>> list(@AuthenticationPrincipal UserPrincipal principal) { return ApiEnvelope.ok(scenarios.list(principal.userId())); }
    @PostMapping public ApiEnvelope<DebugTelemetryContracts.View> create(@AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String key, @Valid @RequestBody DebugTelemetryContracts.CreateRequest request) { return ApiEnvelope.ok(scenarios.create(principal.userId(), key, request)); }
    @PatchMapping("/{id}") public ApiEnvelope<DebugTelemetryContracts.View> update(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id, @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody DebugTelemetryContracts.UpdateRequest request) { return ApiEnvelope.ok(scenarios.update(principal.userId(), id, key, request)); }
    @DeleteMapping("/{id}") public ResponseEntity<ApiEnvelope<Void>> delete(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id, @RequestHeader(value = "Idempotency-Key", required = false) String key) { scenarios.delete(principal.userId(), id, key); return ResponseEntity.ok(ApiEnvelope.ok(null)); }
}
