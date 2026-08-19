package com.xianzhi.fridge.telemetry.application;

import com.xianzhi.fridge.fridge.domain.DeviceStatus;
import com.xianzhi.fridge.fridge.domain.DeviceType;
import com.xianzhi.fridge.fridge.infrastructure.Device;
import com.xianzhi.fridge.fridge.infrastructure.DeviceRepository;
import com.xianzhi.fridge.identity.infrastructure.AppUser;
import com.xianzhi.fridge.identity.infrastructure.AppUserRepository;
import com.xianzhi.fridge.telemetry.config.TelemetryProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class MqttAccessService {
    private static final Logger log = LoggerFactory.getLogger(MqttAccessService.class);
    private final DeviceRepository devices;
    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final TelemetryProperties properties;

    public MqttAccessService(DeviceRepository devices, AppUserRepository users, PasswordEncoder passwords,
                             TelemetryProperties properties) {
        this.devices = devices;
        this.users = users;
        this.passwords = passwords;
        this.properties = properties;
    }

    public boolean authenticate(String username, String clientId, String password) {
        return service(username, password) || simulator(username, password) || device(username, clientId, password);
    }

    public boolean authorize(String username, String clientId, String actionValue, String topic) {
        String action = actionValue == null ? "" : actionValue.toLowerCase();
        boolean allowed;
        if (properties.getServiceUsername().equals(username)) {
            allowed = action.contains("subscribe") && "smart-fridge/v1/+/telemetry".equals(topic);
        } else if (properties.getSimulatorUsername().equals(username)) {
            allowed = action.contains("publish") && virtualTopic(topic);
        } else {
            Device device = devices.findByMqttUsernameAndDeletedAtIsNull(username).orElse(null);
            allowed = available(device) && action.contains("publish")
                    && ("smart-fridge/v1/" + device.getId() + "/telemetry").equals(topic);
        }
        if (!allowed) {
            log.warn("MQTT authorization denied for username={}, clientId={}, action={}, topic={}",
                    username, clientId, actionValue, topic);
        }
        return allowed;
    }

    private boolean service(String username, String password) {
        return properties.getServiceUsername().equals(username) && secure(properties.getServicePassword(), password);
    }

    private boolean simulator(String username, String password) {
        return properties.getSimulatorUsername().equals(username) && secure(properties.getSimulatorPassword(), password);
    }

    private boolean device(String username, String clientId, String password) {
        return devices.findByMqttUsernameAndDeletedAtIsNull(username)
                .filter(this::available)
                .filter(value -> value.getMqttClientId().equals(clientId))
                .map(value -> passwords.matches(password, value.getCredentialHash()))
                .orElse(false);
    }

    private boolean available(Device device) {
        return device != null && device.getStatus() == DeviceStatus.ACTIVE && device.getDeletedAt() == null
                && users.findById(device.getUserId()).filter(AppUser::isAvailable).isPresent();
    }

    private boolean virtualTopic(String topic) {
        if (topic == null || !topic.matches("smart-fridge/v1/[0-9a-fA-F-]{36}/telemetry")) return false;
        String[] parts = topic.split("/");
        try {
            return devices.findById(UUID.fromString(parts[2]))
                    .filter(value -> value.getType() == DeviceType.VIRTUAL && available(value)).isPresent();
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean secure(String expected, String actual) {
        return expected != null && actual != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
