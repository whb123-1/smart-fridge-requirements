package com.xianzhi.fridge.telemetry.api;

import com.xianzhi.fridge.fridge.domain.SensorMetric;
import com.xianzhi.fridge.shared.security.UserPrincipal;
import com.xianzhi.fridge.shared.web.ApiEnvelope;
import com.xianzhi.fridge.telemetry.application.EnvironmentQueryService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class EnvironmentController {
    private final EnvironmentQueryService environment;
    public EnvironmentController(EnvironmentQueryService environment) { this.environment = environment; }
    @GetMapping("/zones/{id}/readings")
    public ApiEnvelope<List<EnvironmentContracts.ReadingView>> readings(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id, @RequestParam(required = false) SensorMetric metric,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Integer limit) { return ApiEnvelope.ok(environment.readings(principal.userId(), id, metric, from, to, limit)); }
    @GetMapping("/fridges/{id}/environment")
    public ApiEnvelope<EnvironmentContracts.FridgeView> environment(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ApiEnvelope.ok(environment.environment(principal.userId(), id));
    }
}
