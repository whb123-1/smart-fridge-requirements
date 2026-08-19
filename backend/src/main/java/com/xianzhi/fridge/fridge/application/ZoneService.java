package com.xianzhi.fridge.fridge.application;

import com.xianzhi.fridge.fridge.api.ZoneContracts;
import com.xianzhi.fridge.fridge.infrastructure.Fridge;
import com.xianzhi.fridge.fridge.infrastructure.FridgeRepository;
import com.xianzhi.fridge.fridge.infrastructure.FridgeZone;
import com.xianzhi.fridge.fridge.infrastructure.FridgeZoneRepository;
import com.xianzhi.fridge.shared.application.AuditService;
import com.xianzhi.fridge.shared.application.IdempotencyService;
import com.xianzhi.fridge.shared.web.ApiException;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ZoneService {
    private final FridgeZoneRepository zones;
    private final FridgeRepository fridges;
    private final IdempotencyService idempotency;
    private final AuditService audit;

    public ZoneService(FridgeZoneRepository zones, FridgeRepository fridges,
                       IdempotencyService idempotency, AuditService audit) {
        this.zones = zones; this.fridges = fridges; this.idempotency = idempotency; this.audit = audit;
    }

    @Transactional
    public ZoneContracts.ZoneView update(UUID userId, UUID zoneId, String key, ZoneContracts.UpdateRequest request) {
        String path = "/api/v1/zones/" + zoneId;
        ZoneContracts.ZoneView replay = idempotency.replay(userId, key, "PATCH", path, request,
                ZoneContracts.ZoneView.class);
        if (replay != null) return replay;
        FridgeZone zone = ownedZone(userId, zoneId);
        String name = request.name().trim();
        boolean duplicate = zones.findByFridgeIdAndDeletedAtIsNullOrderByCreatedAtAsc(zone.getFridgeId()).stream()
                .filter(candidate -> !candidate.getId().equals(zoneId))
                .anyMatch(candidate -> candidate.getName().trim().toLowerCase(Locale.ROOT)
                        .equals(name.toLowerCase(Locale.ROOT)));
        if (duplicate) {
            throw new ApiException(HttpStatus.CONFLICT, "ZONE_NAME_CONFLICT", "Zone name already exists");
        }
        zone.updateSettings(name, request.targetTemperatureC(), request.targetHumidityPct());
        zones.save(zone);
        ZoneContracts.ZoneView result = new ZoneContracts.ZoneView(zone.getId(), zone.getName(),
                zone.getTargetTemperatureC(), zone.getTargetHumidityPct());
        idempotency.save(userId, key, "PATCH", path, request, result, 200);
        audit.record(userId, "ZONE_SETTINGS_UPDATED");
        return result;
    }

    private FridgeZone ownedZone(UUID userId, UUID zoneId) {
        FridgeZone zone = zones.findById(zoneId).filter(candidate -> candidate.getDeletedAt() == null)
                .orElseThrow(() -> notFound());
        Fridge fridge = fridges.findById(zone.getFridgeId())
                .filter(candidate -> userId.equals(candidate.getUserId()) && candidate.getDeletedAt() == null)
                .orElseThrow(() -> notFound());
        return zone;
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ZONE_NOT_FOUND", "Zone not found");
    }
}
