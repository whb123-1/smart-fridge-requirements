package com.xianzhi.fridge.telemetry.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xianzhi.fridge.fridge.domain.SensorMetric;
import com.xianzhi.fridge.fridge.domain.ZoneDefaults;
import com.xianzhi.fridge.fridge.domain.ZoneKind;
import com.xianzhi.fridge.fridge.infrastructure.Fridge;
import com.xianzhi.fridge.fridge.infrastructure.FridgeRepository;
import com.xianzhi.fridge.fridge.infrastructure.FridgeZone;
import com.xianzhi.fridge.fridge.infrastructure.FridgeZoneRepository;
import com.xianzhi.fridge.fridge.infrastructure.SensorSlot;
import com.xianzhi.fridge.fridge.infrastructure.SensorSlotRepository;
import com.xianzhi.fridge.notification.application.NotificationService;
import com.xianzhi.fridge.telemetry.domain.IncidentReason;
import com.xianzhi.fridge.telemetry.infrastructure.EnvironmentIncident;
import com.xianzhi.fridge.telemetry.infrastructure.EnvironmentIncidentRepository;
import com.xianzhi.fridge.telemetry.infrastructure.SensorReadingStore;
import com.xianzhi.fridge.telemetry.infrastructure.ZoneEnvironmentState;
import com.xianzhi.fridge.telemetry.infrastructure.ZoneEnvironmentStateRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EnvironmentAggregationServiceTest {
    private static final Instant START = Instant.parse("2026-08-18T06:00:00Z");
    private final UUID userId = UUID.randomUUID();
    private final UUID fridgeId = UUID.randomUUID();
    private final UUID zoneId = UUID.randomUUID();
    private final UUID sensorId = UUID.randomUUID();

    private SensorSlotRepository sensors;
    private SensorReadingStore readings;
    private ZoneEnvironmentStateRepository states;
    private EnvironmentIncidentRepository incidents;
    private NotificationService notifications;
    private ShelfLifeRiskService risk;
    private AtomicReference<Instant> now;
    private AtomicReference<List<SensorReadingStore.AggregateRow>> fresh;
    private Map<SensorMetric, ZoneEnvironmentState> storedStates;
    private List<EnvironmentIncident> storedIncidents;
    private SensorSlot sensor;
    private EnvironmentAggregationService service;

    @BeforeEach
    void setUp() {
        FridgeZoneRepository zones = mock(FridgeZoneRepository.class);
        FridgeRepository fridges = mock(FridgeRepository.class);
        sensors = mock(SensorSlotRepository.class);
        readings = mock(SensorReadingStore.class);
        states = mock(ZoneEnvironmentStateRepository.class);
        incidents = mock(EnvironmentIncidentRepository.class);
        notifications = mock(NotificationService.class);
        risk = mock(ShelfLifeRiskService.class);
        Clock clock = mock(Clock.class);

        Fridge fridge = new Fridge(fridgeId, userId, "test fridge");
        FridgeZone zone = new FridgeZone(zoneId, fridgeId, ZoneKind.CHILL, "冷藏区", ZoneDefaults.forKind(ZoneKind.CHILL));
        sensor = new SensorSlot(sensorId, zoneId, SensorMetric.TEMPERATURE, 0);
        sensor.bind(UUID.randomUUID(), UUID.randomUUID(), "temperature", "temp-1");

        now = new AtomicReference<>(START);
        fresh = new AtomicReference<>(List.of());
        storedStates = new EnumMap<>(SensorMetric.class);
        storedIncidents = new ArrayList<>();

        when(clock.instant()).thenAnswer(invocation -> now.get());
        when(zones.findByEnabledTrueAndDeletedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(zone));
        when(fridges.findById(fridgeId)).thenReturn(Optional.of(fridge));
        when(sensors.findByZoneIdAndEnabledTrueOrderBySlotIndexAsc(zoneId)).thenReturn(List.of(sensor));
        when(readings.latestGoodByZone(eq(zoneId), any())).thenAnswer(invocation -> fresh.get());
        when(states.findByZoneIdAndMetric(eq(zoneId), any())).thenAnswer(invocation ->
                Optional.ofNullable(storedStates.get(invocation.getArgument(1))));
        when(states.save(any(ZoneEnvironmentState.class))).thenAnswer(invocation -> {
            ZoneEnvironmentState state = invocation.getArgument(0);
            storedStates.put(state.getMetric(), state);
            return state;
        });
        when(incidents.findByZoneIdAndMetricAndReasonAndStatus(eq(zoneId), any(), any(), eq("OPEN")))
                .thenAnswer(invocation -> storedIncidents.stream().filter(value ->
                        value.getMetric() == invocation.getArgument(1)
                                && value.getReason() == invocation.getArgument(2)
                                && "OPEN".equals(value.getStatus())).findFirst());
        when(incidents.save(any(EnvironmentIncident.class))).thenAnswer(invocation -> {
            EnvironmentIncident incident = invocation.getArgument(0);
            if (!storedIncidents.contains(incident)) storedIncidents.add(incident);
            return incident;
        });

        service = new EnvironmentAggregationService(zones, fridges, sensors, readings, states, incidents,
                notifications, risk, clock);
    }

    @Test
    void opensAfterFifteenMinutesAndClosesAfterFifteenNormalMinutes() {
        fresh.set(List.of(reading(new BigDecimal("8"), START)));
        service.aggregateAll();
        assertThat(storedIncidents).isEmpty();

        now.set(START.plus(Duration.ofMinutes(15)));
        fresh.set(List.of(reading(new BigDecimal("8"), now.get())));
        service.aggregateAll();
        EnvironmentIncident incident = storedIncidents.stream()
                .filter(value -> value.getReason() == IncidentReason.OUT_OF_RANGE).findFirst().orElseThrow();
        assertThat(incident.getStartedAt()).isEqualTo(START);
        verify(notifications).ensureIncident(eq(incident), any(), any());

        now.set(START.plus(Duration.ofMinutes(30)));
        fresh.set(List.of(reading(new BigDecimal("4"), now.get())));
        service.aggregateAll();
        assertThat(incident.getStatus()).isEqualTo("OPEN");

        now.set(START.plus(Duration.ofMinutes(45)));
        fresh.set(List.of(reading(new BigDecimal("4"), now.get())));
        service.aggregateAll();
        assertThat(incident.getStatus()).isEqualTo("CLOSED");
        assertThat(incident.getEndedAt()).isEqualTo(now.get());
        verify(notifications).resolveIncident(incident);
    }

    @Test
    void pendingSlotIsNoSensorWhileBoundSlotWithoutFreshDataIsStale() {
        sensor.unbind();
        service.aggregateAll();
        assertThat(storedStates.get(SensorMetric.TEMPERATURE).getCurrentQuality()).isEqualTo("NO_SENSOR");
        verify(notifications, never()).ensureIncident(any(), any(), any());

        sensor.bind(UUID.randomUUID(), UUID.randomUUID(), "temperature", "temp-1");
        sensor.accept(BigDecimal.valueOf(4), "C", START.minus(Duration.ofMinutes(20)), START.minus(Duration.ofMinutes(20)));
        now.set(START.plus(Duration.ofMinutes(1)));
        service.aggregateAll();

        assertThat(storedStates.get(SensorMetric.TEMPERATURE).getCurrentQuality()).isEqualTo("STALE");
        EnvironmentIncident stale = storedIncidents.stream()
                .filter(value -> value.getReason() == IncidentReason.STALE_DATA).findFirst().orElseThrow();
        verify(notifications).ensureIncident(eq(stale), any(), any());
    }

    private SensorReadingStore.AggregateRow reading(BigDecimal value, Instant at) {
        return new SensorReadingStore.AggregateRow(sensorId, SensorMetric.TEMPERATURE, value, at, at);
    }
}
