package com.xianzhi.fridge.telemetry.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianzhi.fridge.fridge.domain.SensorMetric;
import com.xianzhi.fridge.fridge.infrastructure.FridgeZone;
import com.xianzhi.fridge.fridge.infrastructure.FridgeZoneRepository;
import com.xianzhi.fridge.inventory.domain.AssessmentSource;
import com.xianzhi.fridge.inventory.domain.AssessmentStatus;
import com.xianzhi.fridge.inventory.domain.BatchStatus;
import com.xianzhi.fridge.inventory.infrastructure.FoodStorageProfile;
import com.xianzhi.fridge.inventory.infrastructure.FoodStorageProfileRepository;
import com.xianzhi.fridge.inventory.infrastructure.InventoryBatch;
import com.xianzhi.fridge.inventory.infrastructure.InventoryBatchRepository;
import com.xianzhi.fridge.inventory.infrastructure.InventoryItem;
import com.xianzhi.fridge.inventory.infrastructure.InventoryItemRepository;
import com.xianzhi.fridge.inventory.infrastructure.ShelfLifeAssessment;
import com.xianzhi.fridge.inventory.infrastructure.ShelfLifeAssessmentRepository;
import com.xianzhi.fridge.shared.domain.UuidV7;
import com.xianzhi.fridge.telemetry.domain.IncidentSeverity;
import com.xianzhi.fridge.telemetry.infrastructure.BatchEnvironmentExposure;
import com.xianzhi.fridge.telemetry.infrastructure.BatchEnvironmentExposureRepository;
import com.xianzhi.fridge.telemetry.infrastructure.EnvironmentIncident;
import com.xianzhi.fridge.telemetry.infrastructure.EnvironmentIncidentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShelfLifeRiskService {
    private static final BigDecimal DEFAULT_HIGH_RISK_MINUTES = BigDecimal.valueOf(720);
    private final EnvironmentIncidentRepository incidents;
    private final BatchEnvironmentExposureRepository exposures;
    private final InventoryBatchRepository batches;
    private final InventoryItemRepository items;
    private final FoodStorageProfileRepository profiles;
    private final ShelfLifeAssessmentRepository assessments;
    private final FridgeZoneRepository zones;
    private final ObjectMapper mapper;
    private final Clock clock;
    public ShelfLifeRiskService(EnvironmentIncidentRepository incidents, BatchEnvironmentExposureRepository exposures,
                                InventoryBatchRepository batches, InventoryItemRepository items,
                                FoodStorageProfileRepository profiles, ShelfLifeAssessmentRepository assessments,
                                FridgeZoneRepository zones, ObjectMapper mapper, Clock clock) {
        this.incidents = incidents; this.exposures = exposures; this.batches = batches; this.items = items;
        this.profiles = profiles; this.assessments = assessments; this.zones = zones; this.mapper = mapper; this.clock = clock;
    }

    @Transactional
    public void accumulate() {
        Instant now = clock.instant();
        for (EnvironmentIncident incident : incidents.findByReasonOrderByStartedAtAsc(com.xianzhi.fridge.telemetry.domain.IncidentReason.OUT_OF_RANGE)) {
            Instant incidentUntil = incident.getEndedAt() == null ? now : incident.getEndedAt();
            for (InventoryBatch batch : batches.findByZoneId(incident.getZoneId())) {
                InventoryItem item = items.findById(batch.getItemId()).orElse(null);
                if (item == null) continue;
                FoodStorageProfile profile = profile(item, incident.getZoneId());
                Instant start = incident.getStartedAt().isAfter(batch.getStoredAt()) ? incident.getStartedAt() : batch.getStoredAt();
                Instant until = batch.getStatus() == BatchStatus.ACTIVE || batch.getUpdatedAt().isAfter(incidentUntil)
                        ? incidentUntil : batch.getUpdatedAt();
                if (!until.isAfter(start)) continue;
                BatchEnvironmentExposure exposure = exposures.findByBatchIdAndIncidentId(batch.getId(), incident.getId())
                        .orElseGet(() -> new BatchEnvironmentExposure(UuidV7.next(), batch.getId(), incident.getId(), start, now));
                if (!until.isAfter(exposure.getProcessedUntil())) continue;
                BigDecimal minutes = minutes(exposure.getProcessedUntil(), until);
                BigDecimal risk = minutes.multiply(coefficient(profile)).multiply(multiplier(profile, incident)).setScale(3, RoundingMode.HALF_UP);
                exposure.add(minutes, risk, until, now); exposures.save(exposure);
                recalculate(batch, profile, incident, exposure, now);
            }
        }
    }

    private void recalculate(InventoryBatch batch, FoodStorageProfile profile, EnvironmentIncident incident,
                             BatchEnvironmentExposure changed, Instant now) {
        ShelfLifeAssessment previous = assessments.findFirstByBatchIdOrderByCalculatedAtDesc(batch.getId()).orElse(null);
        if (previous == null) return;
        BigDecimal totalRisk = exposures.findByBatchId(batch.getId()).stream().map(BatchEnvironmentExposure::getRiskMinutes)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(3, RoundingMode.HALF_UP);
        if (totalRisk.compareTo(previous.getCumulativeRiskMinutes()) <= 0) return;
        Instant base = previous.getBaseExpiryAt();
        Instant estimated = base == null ? null : subtractMinutes(base, totalRisk);
        BigDecimal highRisk = profile == null ? DEFAULT_HIGH_RISK_MINUTES : profile.getHighRiskMinutes();
        AssessmentStatus status = base == null ? AssessmentStatus.UNKNOWN
                : totalRisk.compareTo(highRisk) >= 0 ? AssessmentStatus.CHECK_BEFORE_CONSUMING
                : estimated.isBefore(now) ? AssessmentStatus.EXPIRED
                : !estimated.isAfter(now.plus(Duration.ofDays(3))) ? AssessmentStatus.EXPIRING_SOON : AssessmentStatus.ADVISORY_ONLY;
        String impact = json(Map.of(
                "incidentId", incident.getId(), "metric", incident.getMetric().name(),
                "severity", incident.getSeverity().name(), "direction", incident.getDirection(),
                "exposureMinutes", changed.getExposureMinutes(), "incidentRiskMinutes", changed.getRiskMinutes(),
                "cumulativeRiskMinutes", totalRisk));
        assessments.save(new ShelfLifeAssessment(UuidV7.next(), batch.getId(),
                profile == null ? previous.getProfileVersion() : profile.getProfileVersion(), estimated, base, totalRisk,
                AssessmentSource.MEASURED_ENVIRONMENT, "MEDIUM", status,
                base == null ? "已记录环境异常，但该批次没有基础到期依据" : "已按实测环境异常累计风险时间；恢复正常后不会返还已扣减期限",
                impact, now));
    }

    private FoodStorageProfile profile(InventoryItem item, java.util.UUID zoneId) {
        String kind = zones.findById(zoneId).map(FridgeZone::getKind).map(Enum::name).orElse(null);
        List<FoodStorageProfile> candidates = profiles.findByCategoryOrderByProfileVersionDesc(item.getCategory());
        return candidates.stream().filter(value -> kind != null && kind.equals(value.getZoneKind())).findFirst()
                .orElseGet(() -> candidates.stream().filter(value -> value.getZoneKind() == null).findFirst().orElse(null));
    }
    private BigDecimal coefficient(FoodStorageProfile profile) { return profile == null ? BigDecimal.ONE : profile.getRiskCoefficient(); }
    private BigDecimal multiplier(FoodStorageProfile profile, EnvironmentIncident incident) {
        if (profile == null) return switch (incident.getSeverity()) { case MILD -> BigDecimal.ONE; case MODERATE -> BigDecimal.valueOf(1.5); case SEVERE -> BigDecimal.valueOf(2.5); };
        BigDecimal moderate = incident.getMetric() == SensorMetric.TEMPERATURE ? profile.getTemperatureModerateDeviationC() : profile.getHumidityModerateDeviationPct();
        BigDecimal severe = incident.getMetric() == SensorMetric.TEMPERATURE ? profile.getTemperatureSevereDeviationC() : profile.getHumiditySevereDeviationPct();
        if (incident.getMaxDeviation().compareTo(severe) >= 0) return profile.getSevereRiskMultiplier();
        if (incident.getMaxDeviation().compareTo(moderate) >= 0) return profile.getModerateRiskMultiplier();
        return profile.getMildRiskMultiplier();
    }
    private static BigDecimal minutes(Instant from, Instant to) { return BigDecimal.valueOf(Duration.between(from, to).toMillis()).divide(BigDecimal.valueOf(60000), 3, RoundingMode.HALF_UP); }
    private static Instant subtractMinutes(Instant base, BigDecimal minutes) { return base.minusMillis(minutes.multiply(BigDecimal.valueOf(60000)).setScale(0, RoundingMode.HALF_UP).longValueExact()); }
    private String json(Map<String, Object> value) { try { return mapper.writeValueAsString(value); } catch (JsonProcessingException exception) { throw new IllegalStateException("Could not serialize environment impact", exception); } }
}
