package com.xianzhi.fridge.telemetry.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianzhi.fridge.fridge.domain.DeviceStatus;
import com.xianzhi.fridge.fridge.domain.DeviceType;
import com.xianzhi.fridge.fridge.domain.SensorMetric;
import com.xianzhi.fridge.fridge.infrastructure.Device;
import com.xianzhi.fridge.fridge.infrastructure.DeviceRepository;
import com.xianzhi.fridge.fridge.infrastructure.SensorProfile;
import com.xianzhi.fridge.fridge.infrastructure.SensorProfileRepository;
import com.xianzhi.fridge.fridge.infrastructure.SensorSlot;
import com.xianzhi.fridge.fridge.infrastructure.SensorSlotRepository;
import com.xianzhi.fridge.telemetry.api.TelemetryContracts;
import com.xianzhi.fridge.telemetry.config.TelemetryProperties;
import com.xianzhi.fridge.telemetry.domain.ReadingQuality;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.security.SecureRandom;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Profile({"worker", "test-worker"})
@ConditionalOnProperty(prefix = "app.telemetry", name = {"enabled", "virtual-simulator-enabled"}, havingValue = "true")
public class TelemetrySimulator {
    private static final Logger log = LoggerFactory.getLogger(TelemetrySimulator.class);
    private final DeviceRepository devices;
    private final SensorSlotRepository sensors;
    private final SensorProfileRepository profiles;
    private final TelemetryProperties properties;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final MeterRegistry meters;
    private final SecureRandom random = new SecureRandom();
    private MqttAsyncClient client;

    public TelemetrySimulator(DeviceRepository devices, SensorSlotRepository sensors, SensorProfileRepository profiles,
                              TelemetryProperties properties, ObjectMapper mapper, Clock clock, MeterRegistry meters) {
        this.devices = devices; this.sensors = sensors; this.profiles = profiles;
        this.properties = properties; this.mapper = mapper; this.clock = clock; this.meters = meters;
        meters.gauge("xianzhi.virtual_simulator.connected", this, value -> value.connected() ? 1 : 0);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() { connectIfNeeded(); }

    @Scheduled(fixedDelayString = "${app.telemetry.emit-interval:PT5S}", initialDelayString = "PT5S")
    public void emit() {
        connectIfNeeded();
        if (!connected()) return;
        Instant now = clock.instant();
        for (Device device : devices.findByTypeAndStatusAndDeletedAtIsNull(DeviceType.VIRTUAL, DeviceStatus.ACTIVE)) {
            for (SensorSlot sensor : sensors.findByDeviceIdAndEnabledTrueOrderBySlotIndexAsc(device.getId())) publish(device, sensor, now);
        }
    }

    private void publish(Device device, SensorSlot sensor, Instant now) {
        SensorProfile profile = profiles.findById(sensor.getProfileId()).orElse(null);
        if (profile == null) return;
        BigDecimal midpoint = profile.getNormalMin().add(profile.getNormalMax()).divide(BigDecimal.valueOf(2), 3, RoundingMode.HALF_UP);
        BigDecimal baseline = sensor.getLastValue() == null ? midpoint : sensor.getLastValue();
        BigDecimal amplitude = sensor.getMetric() == SensorMetric.TEMPERATURE ? BigDecimal.valueOf(0.3) : BigDecimal.valueOf(2.0);
        BigDecimal value = baseline.add(amplitude.multiply(BigDecimal.valueOf(random.nextDouble() * 2 - 1)))
                .max(profile.getNormalMin()).min(profile.getNormalMax()).setScale(3, RoundingMode.HALF_UP);
        TelemetryContracts.Message payload = new TelemetryContracts.Message(UUID.randomUUID(), now, "virtual-simulator",
                List.of(new TelemetryContracts.Reading(sensor.getId(), sensor.getMetric(), value,
                        sensor.getMetric() == SensorMetric.TEMPERATURE ? "C" : "PERCENT", ReadingQuality.GOOD)));
        try {
            MqttMessage message = new MqttMessage(mapper.writeValueAsBytes(payload));
            message.setQos(1); message.setRetained(false);
            client.publish("smart-fridge/v1/" + device.getId() + "/telemetry", message).waitForCompletion();
            meters.counter("xianzhi.virtual_simulator.published").increment();
            log.info("virtual telemetry published deviceId={}, sensorId={}, metric={}, observedAt={}",
                    device.getId(), sensor.getId(), sensor.getMetric(), now);
        } catch (JsonProcessingException | MqttException exception) {
            meters.counter("xianzhi.virtual_simulator.publish_failures").increment();
            log.warn("virtual telemetry publish failed deviceId={}, sensorId={}, reason={}",
                    device.getId(), sensor.getId(), exception.getMessage());
        }
    }

    private synchronized void connectIfNeeded() {
        if (connected()) return;
        closeClient();
        try {
            client = new MqttAsyncClient(
                    properties.getBrokerUrl(),
                    "xianzhi-worker-simulator-" + UUID.randomUUID(),
                    new MemoryPersistence());
            MqttConnectionOptions options = new MqttConnectionOptions();
            options.setUserName(properties.getServiceUsername());
            options.setPassword(properties.getServicePassword().getBytes(StandardCharsets.UTF_8));
            options.setAutomaticReconnect(true); options.setCleanStart(false); options.setSessionExpiryInterval(86400L);
            client.connect(options).waitForCompletion();
            log.info("virtual simulator connected to MQTT broker {}", properties.getBrokerUrl());
        } catch (MqttException exception) {
            meters.counter("xianzhi.virtual_simulator.connection_failures").increment(); closeClient();
            log.warn("virtual simulator MQTT connection failed: {}", exception.getMessage());
        }
    }

    private boolean connected() { return client != null && client.isConnected(); }
    @PreDestroy public synchronized void stop() { closeClient(); }
    private void closeClient() { if (client == null) return; try { if (client.isConnected()) client.disconnect().waitForCompletion(); client.close(); } catch (MqttException ignored) { } finally { client = null; } }
}
