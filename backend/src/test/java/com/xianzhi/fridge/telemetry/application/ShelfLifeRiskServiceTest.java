package com.xianzhi.fridge.telemetry.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.xianzhi.fridge.fridge.domain.SensorMetric;
import com.xianzhi.fridge.fridge.domain.ZoneDefaults;
import com.xianzhi.fridge.fridge.domain.ZoneKind;
import com.xianzhi.fridge.fridge.infrastructure.FridgeZone;
import com.xianzhi.fridge.fridge.infrastructure.FridgeZoneRepository;
import com.xianzhi.fridge.inventory.domain.AssessmentSource;
import com.xianzhi.fridge.inventory.domain.AssessmentStatus;
import com.xianzhi.fridge.inventory.domain.FoodCategory;
import com.xianzhi.fridge.inventory.infrastructure.FoodStorageProfile;
import com.xianzhi.fridge.inventory.infrastructure.FoodStorageProfileRepository;
import com.xianzhi.fridge.inventory.infrastructure.InventoryBatch;
import com.xianzhi.fridge.inventory.infrastructure.InventoryBatchRepository;
import com.xianzhi.fridge.inventory.infrastructure.InventoryItem;
import com.xianzhi.fridge.inventory.infrastructure.InventoryItemRepository;
import com.xianzhi.fridge.inventory.infrastructure.ShelfLifeAssessment;
import com.xianzhi.fridge.inventory.infrastructure.ShelfLifeAssessmentRepository;
import com.xianzhi.fridge.telemetry.domain.IncidentReason;
import com.xianzhi.fridge.telemetry.domain.IncidentSeverity;
import com.xianzhi.fridge.telemetry.infrastructure.BatchEnvironmentExposure;
import com.xianzhi.fridge.telemetry.infrastructure.BatchEnvironmentExposureRepository;
import com.xianzhi.fridge.telemetry.infrastructure.EnvironmentIncident;
import com.xianzhi.fridge.telemetry.infrastructure.EnvironmentIncidentRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ShelfLifeRiskServiceTest {
    @Test
    void severeExposureUsesProfileMultiplierAndNeverReversesAfterRecovery() throws Exception {
        Instant startedAt = Instant.parse("2026-08-18T06:00:00Z");
        AtomicReference<Instant> now = new AtomicReference<>(startedAt.plus(Duration.ofMinutes(60)));
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(invocation -> now.get());

        UUID userId = UUID.randomUUID();
        UUID fridgeId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        EnvironmentIncident incident = new EnvironmentIncident(UUID.randomUUID(), userId, fridgeId, zoneId,
                SensorMetric.TEMPERATURE, IncidentReason.OUT_OF_RANGE, "HIGH", IncidentSeverity.SEVERE,
                startedAt, startedAt, new BigDecimal("6"), startedAt);
        InventoryBatch batch = new InventoryBatch(batchId, itemId, zoneId, startedAt, null, null, null,
                BigDecimal.ONE, "g", null);
        InventoryItem item = new InventoryItem(itemId, userId, fridgeId, null, "test food",
                FoodCategory.MEAT_EGG, null, "g");
        FridgeZone zone = new FridgeZone(zoneId, fridgeId, ZoneKind.CHILL, "冷藏区", ZoneDefaults.forKind(ZoneKind.CHILL));
        FoodStorageProfile profile = profile();
        Instant baseExpiry = startedAt.plus(Duration.ofDays(10));
        AtomicReference<ShelfLifeAssessment> latest = new AtomicReference<>(new ShelfLifeAssessment(
                UUID.randomUUID(), batchId, 3, baseExpiry, baseExpiry, AssessmentSource.PACKAGE_EXPIRY,
                "HIGH", AssessmentStatus.ADVISORY_ONLY, "base"));
        AtomicReference<BatchEnvironmentExposure> exposure = new AtomicReference<>();

        EnvironmentIncidentRepository incidents = mock(EnvironmentIncidentRepository.class);
        BatchEnvironmentExposureRepository exposures = mock(BatchEnvironmentExposureRepository.class);
        InventoryBatchRepository batches = mock(InventoryBatchRepository.class);
        InventoryItemRepository items = mock(InventoryItemRepository.class);
        FoodStorageProfileRepository profiles = mock(FoodStorageProfileRepository.class);
        ShelfLifeAssessmentRepository assessments = mock(ShelfLifeAssessmentRepository.class);
        FridgeZoneRepository zones = mock(FridgeZoneRepository.class);

        when(incidents.findByReasonOrderByStartedAtAsc(IncidentReason.OUT_OF_RANGE)).thenReturn(List.of(incident));
        when(batches.findByZoneId(zoneId)).thenReturn(List.of(batch));
        when(items.findById(itemId)).thenReturn(Optional.of(item));
        when(zones.findById(zoneId)).thenReturn(Optional.of(zone));
        when(profiles.findByCategoryOrderByProfileVersionDesc(FoodCategory.MEAT_EGG)).thenReturn(List.of(profile));
        when(exposures.findByBatchIdAndIncidentId(batchId, incident.getId()))
                .thenAnswer(invocation -> Optional.ofNullable(exposure.get()));
        when(exposures.save(any(BatchEnvironmentExposure.class))).thenAnswer(invocation -> {
            BatchEnvironmentExposure value = invocation.getArgument(0); exposure.set(value); return value;
        });
        when(exposures.findByBatchId(batchId)).thenAnswer(invocation ->
                exposure.get() == null ? List.of() : List.of(exposure.get()));
        when(assessments.findFirstByBatchIdOrderByCalculatedAtDesc(batchId))
                .thenAnswer(invocation -> Optional.ofNullable(latest.get()));
        when(assessments.save(any(ShelfLifeAssessment.class))).thenAnswer(invocation -> {
            ShelfLifeAssessment value = invocation.getArgument(0); latest.set(value); return value;
        });

        ShelfLifeRiskService service = new ShelfLifeRiskService(incidents, exposures, batches, items, profiles,
                assessments, zones, JsonMapper.builder().findAndAddModules().build(), clock);

        service.accumulate();
        assertThat(exposure.get().getExposureMinutes()).isEqualByComparingTo("60.000");
        assertThat(exposure.get().getRiskMinutes()).isEqualByComparingTo("300.000");
        assertThat(latest.get().getCumulativeRiskMinutes()).isEqualByComparingTo("300.000");
        assertThat(latest.get().getEstimatedExpiryAt()).isEqualTo(baseExpiry.minus(Duration.ofMinutes(300)));
        assertThat(latest.get().getSafetyStatus()).isEqualTo(AssessmentStatus.CHECK_BEFORE_CONSUMING);
        assertThat(latest.get().getEstimationSource()).isEqualTo(AssessmentSource.MEASURED_ENVIRONMENT);

        now.set(startedAt.plus(Duration.ofMinutes(90)));
        service.accumulate();
        assertThat(latest.get().getCumulativeRiskMinutes()).isEqualByComparingTo("450.000");
        Instant shortenedExpiry = latest.get().getEstimatedExpiryAt();

        incident.close(now.get());
        now.set(startedAt.plus(Duration.ofMinutes(120)));
        service.accumulate();
        assertThat(exposure.get().getRiskMinutes()).isEqualByComparingTo("450.000");
        assertThat(latest.get().getEstimatedExpiryAt()).isEqualTo(shortenedExpiry);
    }

    private FoodStorageProfile profile() throws Exception {
        var constructor = FoodStorageProfile.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        FoodStorageProfile profile = constructor.newInstance();
        ReflectionTestUtils.setField(profile, "category", FoodCategory.MEAT_EGG);
        ReflectionTestUtils.setField(profile, "zoneKind", "CHILL");
        ReflectionTestUtils.setField(profile, "profileVersion", 3);
        ReflectionTestUtils.setField(profile, "riskCoefficient", new BigDecimal("2.000"));
        ReflectionTestUtils.setField(profile, "temperatureModerateDeviationC", new BigDecimal("2.000"));
        ReflectionTestUtils.setField(profile, "temperatureSevereDeviationC", new BigDecimal("5.000"));
        ReflectionTestUtils.setField(profile, "humidityModerateDeviationPct", new BigDecimal("10.000"));
        ReflectionTestUtils.setField(profile, "humiditySevereDeviationPct", new BigDecimal("20.000"));
        ReflectionTestUtils.setField(profile, "mildRiskMultiplier", new BigDecimal("1.000"));
        ReflectionTestUtils.setField(profile, "moderateRiskMultiplier", new BigDecimal("1.500"));
        ReflectionTestUtils.setField(profile, "severeRiskMultiplier", new BigDecimal("2.500"));
        ReflectionTestUtils.setField(profile, "highRiskMinutes", new BigDecimal("300.000"));
        return profile;
    }
}
