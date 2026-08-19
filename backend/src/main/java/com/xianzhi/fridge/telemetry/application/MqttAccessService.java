package com.xianzhi.fridge.telemetry.application;

import com.xianzhi.fridge.fridge.domain.DeviceStatus;
import com.xianzhi.fridge.fridge.domain.DeviceType;
import com.xianzhi.fridge.fridge.infrastructure.DeviceRepository;
import com.xianzhi.fridge.telemetry.config.TelemetryProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MqttAccessService {
    private static final Logger log = LoggerFactory.getLogger(MqttAccessService.class);
    private final DeviceRepository devices;
    private final TelemetryProperties properties;

    public MqttAccessService(DeviceRepository devices, TelemetryProperties properties) {
        this.devices = devices;
        this.properties = properties;
    }

    public boolean authenticate(String username, String clientId, String password) {
        return service(username, password);
    }

    public boolean authorize(String username, String clientId, String actionValue, String topic) {
        String action = actionValue == null ? "" : actionValue.toLowerCase();
        boolean allowed;
        if (properties.getServiceUsername().equals(username)) {
            allowed = (action.contains("subscribe") && "smart-fridge/v1/+/telemetry".equals(topic))
                    || (action.contains("publish") && virtualTopic(topic));
        } else allowed = false;
        if (!allowed) {
            log.warn("MQTT authorization denied for username={}, clientId={}, action={}, topic={}",
                    username, clientId, actionValue, topic);
        }
        return allowed;
    }

    private boolean service(String username, String password) {
        return properties.getServiceUsername().equals(username) && secure(properties.getServicePassword(), password);
    }

    private boolean virtualTopic(String topic) {
        if (topic == null || !topic.matches("smart-fridge/v1/[0-9a-fA-F-]{36}/telemetry")) return false;
        String[] parts = topic.split("/");
        try {
            return devices.findById(UUID.fromString(parts[2]))
                    .filter(value -> value.getType() == DeviceType.VIRTUAL && value.getStatus() == DeviceStatus.ACTIVE
                            && value.getDeletedAt() == null).isPresent();
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean secure(String expected, String actual) {
        return expected != null && actual != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
