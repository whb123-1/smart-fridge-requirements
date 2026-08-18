package com.xianzhi.fridge.telemetry.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("simulator")
public class TelemetrySimulator {
    private final DebugTelemetryScenarioRepository scenarios; private final DeviceRepository devices;
    private final SensorSlotRepository sensors; private final SensorProfileRepository profiles;
    private final TelemetryProperties properties; private final ObjectMapper mapper; private final Clock clock;
    private final SecureRandom random = new SecureRandom(); private MqttAsyncClient client;
    public TelemetrySimulator(DebugTelemetryScenarioRepository scenarios, DeviceRepository devices, SensorSlotRepository sensors,
                              SensorProfileRepository profiles, TelemetryProperties properties, ObjectMapper mapper, Clock clock) {
        this.scenarios = scenarios; this.devices = devices; this.sensors = sensors; this.profiles = profiles;
        this.properties = properties; this.mapper = mapper; this.clock = clock;
    }
    @EventListener(ApplicationReadyEvent.class)
    public void start() throws MqttException {
        client = new MqttAsyncClient(properties.getBrokerUrl(), "xianzhi-simulator-" + UUID.randomUUID());
        MqttConnectionOptions options = new MqttConnectionOptions(); options.setUserName(properties.getSimulatorUsername());
        options.setPassword(properties.getSimulatorPassword().getBytes(StandardCharsets.UTF_8)); options.setAutomaticReconnect(true);
        options.setCleanStart(false); options.setSessionExpiryInterval(86400L); client.connect(options).waitForCompletion();
    }
    @Scheduled(fixedDelayString = "${app.telemetry.simulator-poll-delay:PT5S}", initialDelayString = "PT5S")
    @Transactional
    public void emitDue() {
        Instant now = clock.instant();
        for (DebugTelemetryScenario scenario : scenarios.findTop100ByStatusAndNextEmitAtLessThanEqualOrderByNextEmitAtAsc("ACTIVE", now)) {
            if (!now.isBefore(scenario.getEndsAt())) { scenario.complete(now); continue; }
            if (!"STALE".equals(scenario.getMode())) publish(scenario, now);
            scenario.emitted(now, properties.getEmitInterval());
        }
    }
    private void publish(DebugTelemetryScenario scenario, Instant now) {
        if (client == null || !client.isConnected()) throw new IllegalStateException("Simulator MQTT client is disconnected");
        Device device = devices.findById(scenario.getDeviceId()).orElseThrow(); SensorSlot sensor = sensors.findById(scenario.getSensorId()).orElseThrow();
        SensorProfile profile = profiles.findById(sensor.getProfileId()).orElseThrow(); BigDecimal baseline = sensor.getLastValue() == null
                ? profile.getNormalMin().add(profile.getNormalMax()).divide(BigDecimal.valueOf(2), 3, RoundingMode.HALF_UP) : sensor.getLastValue();
        BigDecimal value = "TARGET".equals(scenario.getMode()) ? approach(baseline, scenario.getTargetValue())
                : profile.getNormalMin().add(profile.getNormalMax()).divide(BigDecimal.valueOf(2), 3, RoundingMode.HALF_UP);
        value = value.add(jitter(scenario.getJitter())).max(profile.getPhysicalMin()).min(profile.getPhysicalMax()).setScale(3, RoundingMode.HALF_UP);
        TelemetryContracts.Message payload = new TelemetryContracts.Message(UUID.randomUUID(), now, "debug-simulator",
                List.of(new TelemetryContracts.Reading(sensor.getId(), sensor.getMetric(), value,
                        sensor.getMetric() == SensorMetric.TEMPERATURE ? "C" : "PERCENT", ReadingQuality.GOOD)));
        try {
            MqttMessage message = new MqttMessage(mapper.writeValueAsBytes(payload)); message.setQos(1); message.setRetained(false);
            client.publish("smart-fridge/v1/" + device.getId() + "/telemetry", message).waitForCompletion();
        } catch (JsonProcessingException | MqttException exception) { throw new IllegalStateException("Could not publish simulated telemetry", exception); }
    }
    private BigDecimal approach(BigDecimal current, BigDecimal target) { return current.add(target.subtract(current).multiply(BigDecimal.valueOf(0.35))); }
    private BigDecimal jitter(BigDecimal amplitude) { if (amplitude == null || amplitude.signum() == 0) return BigDecimal.ZERO; return amplitude.multiply(BigDecimal.valueOf(random.nextDouble() * 2 - 1)); }
    @PreDestroy public void stop() { try { if (client != null && client.isConnected()) client.disconnect().waitForCompletion(); } catch (MqttException ignored) { } }
}
