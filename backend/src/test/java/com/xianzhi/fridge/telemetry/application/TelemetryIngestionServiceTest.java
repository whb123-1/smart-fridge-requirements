package com.xianzhi.fridge.telemetry.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.xianzhi.fridge.fridge.domain.DeviceType;
import com.xianzhi.fridge.fridge.domain.SensorMetric;
import com.xianzhi.fridge.fridge.domain.ZoneDefaults;
import com.xianzhi.fridge.fridge.domain.ZoneKind;
import com.xianzhi.fridge.fridge.infrastructure.Device;
import com.xianzhi.fridge.fridge.infrastructure.DeviceRepository;
import com.xianzhi.fridge.fridge.infrastructure.FridgeZone;
import com.xianzhi.fridge.fridge.infrastructure.FridgeZoneRepository;
import com.xianzhi.fridge.fridge.infrastructure.SensorProfile;
import com.xianzhi.fridge.fridge.infrastructure.SensorProfileRepository;
import com.xianzhi.fridge.fridge.infrastructure.SensorSlot;
import com.xianzhi.fridge.fridge.infrastructure.SensorSlotRepository;
import com.xianzhi.fridge.shared.application.AuditService;
import com.xianzhi.fridge.shared.infrastructure.OutboxEvent;
import com.xianzhi.fridge.shared.infrastructure.OutboxEventRepository;
import com.xianzhi.fridge.telemetry.api.TelemetryContracts;
import com.xianzhi.fridge.telemetry.domain.ReadingQuality;
import com.xianzhi.fridge.telemetry.domain.TelemetryResult;
import com.xianzhi.fridge.telemetry.infrastructure.SensorReadingStore;
import com.xianzhi.fridge.telemetry.infrastructure.TelemetryMessage;
import com.xianzhi.fridge.telemetry.infrastructure.TelemetryMessageRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class TelemetryIngestionServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-18T06:00:00Z");
    private final UUID userId = UUID.randomUUID();
    private final UUID fridgeId = UUID.randomUUID();
    private final UUID zoneId = UUID.randomUUID();
    private final UUID deviceId = UUID.randomUUID();
    private final UUID sensorId = UUID.randomUUID();
    private final UUID profileId = UUID.randomUUID();

    private DeviceRepository devices;
    private SensorSlotRepository sensors;
    private SensorProfileRepository profiles;
    private FridgeZoneRepository zones;
    private TelemetryMessageRepository messages;
    private SensorReadingStore readings;
    private OutboxEventRepository outbox;
    private AuditService audit;
    private ObjectMapper mapper;
    private SensorSlot sensor;
    private TelemetryIngestionService service;

    @BeforeEach
    void setUp() throws Exception {
        devices = mock(DeviceRepository.class);
        sensors = mock(SensorSlotRepository.class);
        profiles = mock(SensorProfileRepository.class);
        zones = mock(FridgeZoneRepository.class);
        messages = mock(TelemetryMessageRepository.class);
        readings = mock(SensorReadingStore.class);
        outbox = mock(OutboxEventRepository.class);
        audit = mock(AuditService.class);
        mapper = JsonMapper.builder().findAndAddModules().build();

        Device device = new Device(deviceId, userId, zoneId, "virtual", DeviceType.VIRTUAL);
        sensor = new SensorSlot(sensorId, zoneId, SensorMetric.TEMPERATURE, 0);
        sensor.bind(deviceId, profileId, "temperature", "temp-1");
        FridgeZone zone = new FridgeZone(zoneId, fridgeId, ZoneKind.CHILL, "冷藏区", ZoneDefaults.forKind(ZoneKind.CHILL));
        SensorProfile profile = profile(new BigDecimal("-50"), new BigDecimal("100"), new BigDecimal("0.500"));

        when(devices.findById(deviceId)).thenReturn(Optional.of(device));
        when(sensors.findByDeviceIdAndEnabledTrueOrderBySlotIndexAsc(deviceId)).thenReturn(List.of(sensor));
        when(profiles.findById(profileId)).thenReturn(Optional.of(profile));
        when(zones.findById(zoneId)).thenReturn(Optional.of(zone));
        when(messages.save(any(TelemetryMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(outbox.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service = new TelemetryIngestionService(devices, sensors, profiles, zones, messages, readings,
                outbox, mapper, audit, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void convertsFahrenheitToCelsiusAndStoresNormalizedReading() throws Exception {
        TelemetryResult result = ingest(NOW.minusSeconds(30), new BigDecimal("50"), "F", ReadingQuality.GOOD);

        assertThat(result).isEqualTo(TelemetryResult.ACCEPTED);
        verify(readings).insert(any(), eq(userId), eq(fridgeId), eq(zoneId), eq(deviceId), eq(sensorId), any(),
                eq(SensorMetric.TEMPERATURE), eq(new BigDecimal("10.000")), eq("C"), eq(ReadingQuality.GOOD),
                any(), eq(NOW.minusSeconds(30)), eq(NOW));
        assertThat(sensor.getLastValue()).isEqualByComparingTo("10.000");
    }

    @Test
    void acceptsPercentSymbolForHumidityAndStoresCanonicalUnit() throws Exception {
        ReflectionTestUtils.setField(sensor, "metric", SensorMetric.HUMIDITY);
        TelemetryContracts.Message message = new TelemetryContracts.Message(UUID.randomUUID(), NOW, "test-firmware",
                List.of(new TelemetryContracts.Reading(sensorId, SensorMetric.HUMIDITY,
                        new BigDecimal("65"), "%", ReadingQuality.GOOD)));

        TelemetryResult result = service.ingest(deviceId, mapper.writeValueAsString(message));

        assertThat(result).isEqualTo(TelemetryResult.ACCEPTED);
        verify(readings).insert(any(), eq(userId), eq(fridgeId), eq(zoneId), eq(deviceId), eq(sensorId), any(),
                eq(SensorMetric.HUMIDITY), eq(new BigDecimal("65")), eq("PERCENT"), eq(ReadingQuality.GOOD),
                any(), eq(NOW), eq(NOW));
        assertThat(sensor.getLastValue()).isEqualByComparingTo("65");
    }

    @Test
    void rejectsFutureAndPhysicalOutOfRangeMessagesAsWholeMessages() throws Exception {
        TelemetryResult future = ingest(NOW.plus(Duration.ofMinutes(11)), BigDecimal.ONE, "C", ReadingQuality.GOOD);
        TelemetryResult physical = ingest(NOW, new BigDecimal("101"), "C", ReadingQuality.GOOD);

        assertThat(future).isEqualTo(TelemetryResult.REJECTED);
        assertThat(physical).isEqualTo(TelemetryResult.REJECTED);
        verify(audit).record(userId, "TELEMETRY_FUTURE");
        verify(audit).record(userId, "TELEMETRY_PHYSICAL_RANGE");
        verifyNoInteractions(readings);
    }

    @Test
    void duplicateIsAcknowledgedWithoutWritingAgain() throws Exception {
        UUID messageId = UUID.randomUUID();
        when(messages.existsByDeviceIdAndMessageId(deviceId, messageId.toString())).thenReturn(true);

        TelemetryResult result = service.ingest(deviceId, payload(messageId, NOW, BigDecimal.ONE, "C", ReadingQuality.GOOD));

        assertThat(result).isEqualTo(TelemetryResult.DUPLICATE);
        verify(messages, never()).save(any());
        verifyNoInteractions(readings);
    }

    @Test
    void outOfOrderReadingIsStoredButDoesNotReplaceCurrentValue() throws Exception {
        sensor.accept(new BigDecimal("4"), "C", NOW.minus(Duration.ofMinutes(5)), NOW.minus(Duration.ofMinutes(5)));

        TelemetryResult result = ingest(NOW.minus(Duration.ofMinutes(10)), new BigDecimal("5"), "C", ReadingQuality.GOOD);

        assertThat(result).isEqualTo(TelemetryResult.ACCEPTED);
        assertThat(sensor.getLastValue()).isEqualByComparingTo("4");
        assertThat(sensor.getLastObservedAt()).isEqualTo(NOW.minus(Duration.ofMinutes(5)));
    }

    @Test
    void badReadingIsAuditedWithoutCreatingReading() throws Exception {
        TelemetryResult result = ingest(NOW, BigDecimal.ONE, "C", ReadingQuality.BAD);

        assertThat(result).isEqualTo(TelemetryResult.ACCEPTED);
        verify(audit).record(userId, "TELEMETRY_BAD_READING");
        verifyNoInteractions(readings);
        assertThat(sensor.getLastValue()).isNull();
    }

    @Test
    void rejectsSensorThatIsNotBoundToTheTopicDevice() throws Exception {
        TelemetryContracts.Message message = new TelemetryContracts.Message(UUID.randomUUID(), NOW, "test-firmware",
                List.of(new TelemetryContracts.Reading(UUID.randomUUID(), SensorMetric.TEMPERATURE,
                        BigDecimal.ONE, "C", ReadingQuality.GOOD)));

        TelemetryResult result = service.ingest(deviceId, mapper.writeValueAsString(message));

        assertThat(result).isEqualTo(TelemetryResult.REJECTED);
        verify(audit).record(userId, "TELEMETRY_SENSOR_OWNERSHIP");
        verifyNoInteractions(readings);
    }

    @Test
    void rejectsReadingMoreThanFortyEightHoursOlderThanLatestValidValue() throws Exception {
        sensor.accept(new BigDecimal("4"), "C", NOW, NOW);

        TelemetryResult result = ingest(NOW.minus(Duration.ofHours(48)).minusMillis(1),
                new BigDecimal("5"), "C", ReadingQuality.GOOD);

        assertThat(result).isEqualTo(TelemetryResult.REJECTED);
        verify(audit).record(userId, "TELEMETRY_TOO_OLD");
        verifyNoInteractions(readings);
    }

    @Test
    void threeConsecutiveRateViolationsEmitOnlyOneSuspectEventAndGoodReadingRecovers() throws Exception {
        sensor.accept(new BigDecimal("4"), "C", NOW.minus(Duration.ofMinutes(10)), NOW.minus(Duration.ofMinutes(10)));
        ingest(NOW.minus(Duration.ofMinutes(9)), new BigDecimal("20"), "C", ReadingQuality.GOOD);
        ingest(NOW.minus(Duration.ofMinutes(8)), new BigDecimal("21"), "C", ReadingQuality.GOOD);
        ingest(NOW.minus(Duration.ofMinutes(7)), new BigDecimal("22"), "C", ReadingQuality.GOOD);

        assertThat(sensor.getConsecutiveSuspectCount()).isEqualTo(3);
        ArgumentCaptor<OutboxEvent> events = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox, org.mockito.Mockito.atLeastOnce()).save(events.capture());
        assertThat(events.getAllValues()).extracting(OutboxEvent::getEventType)
                .containsOnlyOnce("SensorSuspectDetected");

        ingest(NOW.minus(Duration.ofMinutes(6)), new BigDecimal("5"), "C", ReadingQuality.GOOD);
        assertThat(sensor.getConsecutiveSuspectCount()).isZero();
        verify(outbox, org.mockito.Mockito.atLeastOnce()).save(events.capture());
        assertThat(events.getAllValues()).extracting(OutboxEvent::getEventType).contains("SensorRecovered");
    }

    private TelemetryResult ingest(Instant observedAt, BigDecimal value, String unit, ReadingQuality quality) throws Exception {
        return service.ingest(deviceId, payload(UUID.randomUUID(), observedAt, value, unit, quality));
    }

    private String payload(UUID messageId, Instant observedAt, BigDecimal value, String unit, ReadingQuality quality) throws Exception {
        return mapper.writeValueAsString(new TelemetryContracts.Message(messageId, observedAt, "test-firmware",
                List.of(new TelemetryContracts.Reading(sensorId, SensorMetric.TEMPERATURE, value, unit, quality))));
    }

    private SensorProfile profile(BigDecimal physicalMin, BigDecimal physicalMax, BigDecimal maxChange) throws Exception {
        var constructor = SensorProfile.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        SensorProfile profile = constructor.newInstance();
        ReflectionTestUtils.setField(profile, "id", profileId);
        ReflectionTestUtils.setField(profile, "zoneKind", ZoneKind.CHILL);
        ReflectionTestUtils.setField(profile, "metric", SensorMetric.TEMPERATURE);
        ReflectionTestUtils.setField(profile, "physicalMin", physicalMin);
        ReflectionTestUtils.setField(profile, "physicalMax", physicalMax);
        ReflectionTestUtils.setField(profile, "normalMin", BigDecimal.ONE);
        ReflectionTestUtils.setField(profile, "normalMax", BigDecimal.valueOf(6));
        ReflectionTestUtils.setField(profile, "maxChangePerMinute", maxChange);
        return profile;
    }
}
