package com.xianzhi.fridge.fridge.api;

import com.xianzhi.fridge.fridge.application.ZoneService;
import com.xianzhi.fridge.shared.security.UserPrincipal;
import com.xianzhi.fridge.shared.web.ApiEnvelope;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/zones")
public class ZoneController {
    private final ZoneService zones;

    public ZoneController(ZoneService zones) { this.zones = zones; }

    @PatchMapping("/{id}")
    public ApiEnvelope<ZoneContracts.ZoneView> update(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id, @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody ZoneContracts.UpdateRequest request) {
        return ApiEnvelope.ok(zones.update(principal.userId(), id, key, request));
    }
}
